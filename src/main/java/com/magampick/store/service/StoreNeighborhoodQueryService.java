package com.magampick.store.service;

import com.magampick.address.service.AddressService;
import com.magampick.clearance.domain.ClearanceItemStatus;
import com.magampick.clearance.repository.ClearanceItemRepository;
import com.magampick.favorite.repository.FavoriteRepository;
import com.magampick.global.common.GeometryUtil;
import com.magampick.global.exception.BusinessException;
import com.magampick.review.service.RatingStats;
import com.magampick.review.service.ReviewQueryService;
import com.magampick.store.dto.NeighborhoodStoreResponse;
import com.magampick.store.repository.StoreCandidate;
import com.magampick.store.repository.StoreRepository;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 홈 피드 우리 동네 마감픽 서비스. 기본 주소지 5km · OPEN · 오늘영업 매장 중 단골 제외, 추천 스코어 정렬, top6 반환.
 *
 * <p>추천 스코어 공식 = {@link StoreQueryService#recommendedScore} 와 동일 소스.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreNeighborhoodQueryService {

  private static final int TOP_N = 6;

  private final AddressService addressService;
  private final StoreRepository storeRepository;
  private final ClearanceItemRepository clearanceItemRepository;
  private final FavoriteRepository favoriteRepository;
  private final ReviewQueryService reviewQueryService;

  /**
   * 우리 동네 마감픽 목록 조회.
   *
   * @param customerId 소비자 ID
   * @return 단골 제외 후 추천 스코어 정렬 top6. 빈 결과이면 빈 리스트.
   * @throws BusinessException DEFAULT_ADDRESS_REQUIRED — 기본 주소지 없을 때
   */
  public List<NeighborhoodStoreResponse> getNeighborhoodStores(Long customerId) {
    // 1. origin — 기본 주소지
    Point defaultLocation = addressService.requireDefaultLocation(customerId);
    double lat = GeometryUtil.latitude(defaultLocation);
    double lng = GeometryUtil.longitude(defaultLocation);
    String today = LocalDate.now().getDayOfWeek().name();

    // 2. PostGIS 후보 쿼리 (5km, OPEN, 오늘 영업)
    List<StoreCandidate> candidates = storeRepository.findOpenStoresWithin5km(lat, lng, today);
    if (candidates.isEmpty()) {
      return List.of();
    }

    List<Long> candidateIds = candidates.stream().map(StoreCandidate::getId).toList();

    // 3. 단골 차집합
    Set<Long> favoriteIds =
        new HashSet<>(
            favoriteRepository.findStoreIdsByCustomerIdAndStoreIdIn(customerId, candidateIds));

    List<StoreCandidate> nonFavorites =
        candidates.stream().filter(c -> !favoriteIds.contains(c.getId())).toList();

    if (nonFavorites.isEmpty()) {
      return List.of();
    }

    List<Long> nonFavoriteIds = nonFavorites.stream().map(StoreCandidate::getId).toList();

    // 4. 배치 enrich
    Map<Long, RatingStats> ratingMap = reviewQueryService.getStoreRatings(nonFavoriteIds);
    Map<Long, Object[]> dealMap = buildDealMap(nonFavoriteIds);

    // 5. 추천 스코어 정렬 → top6
    return nonFavorites.stream()
        .map(c -> enrich(c, ratingMap, dealMap))
        .sorted(
            Comparator.comparingDouble(
                    (NeighborhoodStoreResponse r) ->
                        // StoreQueryService.recommendedScore 와 동일 공식 단일 소스
                        StoreQueryService.recommendedScore(
                            r.distanceKm(), r.rating(), r.activeDealCount()))
                .reversed())
        .limit(TOP_N)
        .toList();
  }

  // ── private helpers ──────────────────────────────────────────────────────────────────────────

  private Map<Long, Object[]> buildDealMap(List<Long> storeIds) {
    return clearanceItemRepository
        .findActiveDealSummaryByStoreIds(storeIds, ClearanceItemStatus.OPEN)
        .stream()
        .collect(Collectors.toMap(row -> ((Number) row[0]).longValue(), row -> row));
  }

  private NeighborhoodStoreResponse enrich(
      StoreCandidate c, Map<Long, RatingStats> ratingMap, Map<Long, Object[]> dealMap) {

    double distanceKm = c.getDistanceMeters() / 1000.0;
    double rating = ratingMap.getOrDefault(c.getId(), RatingStats.EMPTY).average();

    Object[] deal = dealMap.get(c.getId());
    long activeDealCount = deal != null ? ((Number) deal[1]).longValue() : 0L;

    return new NeighborhoodStoreResponse(
        c.getId(), c.getName(), c.getImageUrl(), distanceKm, rating, activeDealCount);
  }
}
