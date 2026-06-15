package com.magampick.favorite.service;

import com.magampick.address.service.AddressService;
import com.magampick.clearance.domain.ClearanceItemStatus;
import com.magampick.clearance.repository.ClearanceItemRepository;
import com.magampick.favorite.domain.Favorite;
import com.magampick.favorite.dto.FavoriteAddResponse;
import com.magampick.favorite.dto.FavoriteListResponse;
import com.magampick.favorite.dto.FavoriteStoreResponse;
import com.magampick.favorite.exception.FavoriteErrorCode;
import com.magampick.favorite.mapper.FavoriteMapper;
import com.magampick.favorite.repository.FavoriteRepository;
import com.magampick.favorite.repository.FavoriteStoreCandidate;
import com.magampick.global.common.GeometryUtil;
import com.magampick.global.exception.BusinessException;
import com.magampick.review.service.RatingStats;
import com.magampick.review.service.ReviewQueryService;
import com.magampick.store.exception.StoreErrorCode;
import com.magampick.store.repository.StoreRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Point;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FavoriteService {

  /** 한 소비자가 등록할 수 있는 단골 매장 최대 개수 (정책 — 노션 "단골매장 추가/해제"). */
  private static final int MAX_FAVORITES_PER_CUSTOMER = 50;

  private final FavoriteRepository favoriteRepository;
  private final StoreRepository storeRepository;
  private final FavoriteMapper favoriteMapper;
  private final FavoriteInserter favoriteInserter;
  private final AddressService addressService;
  private final ReviewQueryService reviewQueryService;
  private final ClearanceItemRepository clearanceItemRepository;

  @Transactional
  public FavoriteAddResponse addFavorite(Long customerId, Long storeId) {
    // 매장 존재 검증
    if (!storeRepository.existsById(storeId)) {
      throw new BusinessException(StoreErrorCode.STORE_NOT_FOUND);
    }

    // 이미 단골이면 멱등 성공 — 한도 체크 건너뜀 (한도 도달 상태에서도 재추가는 통과)
    Optional<Favorite> existing =
        favoriteRepository.findByCustomerIdAndStoreId(customerId, storeId);
    if (existing.isPresent()) {
      return favoriteMapper.toAddResponse(existing.get());
    }

    // 신규 등록 — 한도(50개) 검증 (주소지 한도와 동일한 count 패턴)
    long currentCount = favoriteRepository.countByCustomerId(customerId);
    if (currentCount >= MAX_FAVORITES_PER_CUSTOMER) {
      throw new BusinessException(FavoriteErrorCode.FAVORITE_LIMIT_REACHED);
    }

    // 단골 등록 — 동시 추가 레이스(unique 위반)는 멱등 성공으로 수렴.
    // INSERT 를 별도 트랜잭션(FavoriteInserter, REQUIRES_NEW)으로 격리해 위반이 바깥 트랜잭션을 오염시키지 않게 한다.
    try {
      FavoriteAddResponse response = favoriteInserter.insert(customerId, storeId);
      log.info("즐겨찾기 등록됨. customerId={}, storeId={}", customerId, storeId);
      return response;
    } catch (DataIntegrityViolationException e) {
      log.info("즐겨찾기 동시 추가 레이스 감지 — 멱등 처리. customerId={}, storeId={}", customerId, storeId);
      return favoriteRepository
          .findByCustomerIdAndStoreId(customerId, storeId)
          .map(favoriteMapper::toAddResponse)
          .orElseThrow(() -> e);
    }
  }

  @Transactional
  public void removeFavorite(Long customerId, Long storeId) {
    favoriteRepository.deleteByCustomerIdAndStoreId(customerId, storeId);
    log.info("즐겨찾기 해제됨. customerId={}, storeId={}", customerId, storeId);
  }

  /**
   * 단골 매장 목록 조회. 기본 주소지 origin 기준 거리 + 배치 평점/떨이 enrich 후, 떨이활성(>0) 우선 → 등록순(createdAt asc) 정렬.
   *
   * @param customerId 소비자 ID
   * @return {@link FavoriteListResponse}
   * @throws BusinessException DEFAULT_ADDRESS_REQUIRED — 기본 주소지 없을 때
   */
  public FavoriteListResponse getFavorites(Long customerId) {
    // 1. 기본 주소지 origin
    Point defaultLocation = addressService.requireDefaultLocation(customerId);
    double lat = GeometryUtil.latitude(defaultLocation);
    double lng = GeometryUtil.longitude(defaultLocation);

    // 2. 단골 매장 후보 조회 (PostGIS 거리 포함, 소프트삭제 제외, 전체 단골)
    List<FavoriteStoreCandidate> candidates =
        favoriteRepository.findFavoriteStoresWithDistance(customerId, lat, lng);

    if (candidates.isEmpty()) {
      return new FavoriteListResponse(List.of(), 0L, 0L);
    }

    List<Long> storeIds = candidates.stream().map(FavoriteStoreCandidate::getStoreId).toList();

    // 3. 배치 enrich (N+1 방지)
    Map<Long, RatingStats> ratingMap = reviewQueryService.getStoreRatings(storeIds);
    Map<Long, Long> dealCountMap = buildDealCountMap(storeIds);

    // 4. 정렬: 떨이활성(activeDealCount>0) 우선 desc → 등록순(createdAt asc) 2차
    List<FavoriteStoreCandidate> sorted =
        candidates.stream()
            .sorted(
                Comparator.comparing(
                        (FavoriteStoreCandidate c) ->
                            dealCountMap.getOrDefault(c.getStoreId(), 0L) > 0 ? 0 : 1)
                    .thenComparing(FavoriteStoreCandidate::getCreatedAt))
            .toList();

    // 5. 아이템 매핑 + 통계 집계
    long totalActiveDealCount = 0L;
    List<FavoriteStoreResponse> stores = new ArrayList<>(sorted.size());
    for (FavoriteStoreCandidate c : sorted) {
      long activeDealCount = dealCountMap.getOrDefault(c.getStoreId(), 0L);
      totalActiveDealCount += activeDealCount;
      double distanceKm = c.getDistanceMeters() / 1000.0;
      double rating = ratingMap.getOrDefault(c.getStoreId(), RatingStats.EMPTY).average();
      stores.add(
          new FavoriteStoreResponse(
              c.getStoreId(), c.getName(), c.getImageUrl(), distanceKm, rating, activeDealCount));
    }

    return new FavoriteListResponse(stores, (long) stores.size(), totalActiveDealCount);
  }

  // ── private helpers ──────────────────────────────────────────────────────────

  /** 매장별 활성(OPEN) 떨이 개수 Map. 결과 없는 storeId 는 Map 에 포함 안 됨 → 호출 측에서 0 기본값 처리. */
  private Map<Long, Long> buildDealCountMap(List<Long> storeIds) {
    return clearanceItemRepository
        .findActiveDealSummaryByStoreIds(storeIds, ClearanceItemStatus.OPEN)
        .stream()
        .collect(
            Collectors.toMap(
                row -> ((Number) row[0]).longValue(), row -> ((Number) row[1]).longValue()));
  }
}
