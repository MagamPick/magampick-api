package com.magampick.store.service;

import com.magampick.address.domain.Address;
import com.magampick.address.exception.AddressErrorCode;
import com.magampick.address.repository.AddressRepository;
import com.magampick.clearance.domain.ClearanceItemStatus;
import com.magampick.clearance.repository.ClearanceItemRepository;
import com.magampick.favorite.repository.FavoriteRepository;
import com.magampick.global.common.GeometryUtil;
import com.magampick.global.exception.BusinessException;
import com.magampick.review.service.RatingStats;
import com.magampick.review.service.ReviewQueryService;
import com.magampick.store.dto.StoreListItemResponse;
import com.magampick.store.dto.StoreListResponse;
import com.magampick.store.dto.StoreSort;
import com.magampick.store.repository.StoreCandidate;
import com.magampick.store.repository.StoreRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소비자 전체 매장 조회 서비스. PostGIS 후보 추출 → 인메모리 enrich(평점·떨이·단골) → 정렬 → 페이징.
 *
 * <p>MVP: 5km 소규모라 후보 N 작음 → 인메모리 정렬/페이징이 단순하고 FE "이미 정렬됨" 모델과 일치. 스케일 시 SQL 정렬+커서로 이전.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreQueryService {

  private final AddressRepository addressRepository;
  private final StoreRepository storeRepository;
  private final ClearanceItemRepository clearanceItemRepository;
  private final FavoriteRepository favoriteRepository;
  private final ReviewQueryService reviewQueryService;

  /**
   * 전체 매장 조회. 기본 주소지 5km 이내 OPEN 매장을 5종 정렬로 반환.
   *
   * @param customerId 소비자 ID
   * @param sort 정렬 기준
   * @param page 페이지 번호 (0-based)
   * @param size 페이지당 항목 수
   * @throws BusinessException DEFAULT_ADDRESS_REQUIRED — 기본 주소지 없을 때
   */
  public StoreListResponse getStores(Long customerId, StoreSort sort, int page, int size) {
    // 1. origin — 기본 주소지
    Address defaultAddress =
        addressRepository
            .findByCustomerIdAndIsDefaultTrue(customerId)
            .orElseThrow(() -> new BusinessException(AddressErrorCode.DEFAULT_ADDRESS_REQUIRED));

    double lat = GeometryUtil.latitude(defaultAddress.getLocation());
    double lng = GeometryUtil.longitude(defaultAddress.getLocation());
    // DayOfWeek.name() = "SATURDAY" 등 — store_business_hours.day_of_week VARCHAR enum 과 일치
    String today = LocalDate.now().getDayOfWeek().name();

    // 2. PostGIS 후보 쿼리
    List<StoreCandidate> candidates = storeRepository.findOpenStoresWithin5km(lat, lng, today);

    if (candidates.isEmpty()) {
      return new StoreListResponse(List.of(), page, size, false, 0L, 0L);
    }

    List<Long> storeIds = candidates.stream().map(StoreCandidate::getId).toList();

    // 3. 배치 enrich (N+1 회피)
    Map<Long, RatingStats> ratingMap = reviewQueryService.getStoreRatings(storeIds);
    Map<Long, Object[]> dealMap = buildDealMap(storeIds);
    Set<Long> favoriteIds =
        new HashSet<>(
            favoriteRepository.findStoreIdsByCustomerIdAndStoreIdIn(customerId, storeIds));

    // 4. EnrichedStore 조립
    List<EnrichedStore> enriched =
        candidates.stream()
            .map(c -> buildEnrichedStore(c, ratingMap, dealMap, favoriteIds))
            .toList();

    // 5. 집계 (페이징 전 전체 후보 기준)
    long total = enriched.size();
    long dealStoreCount = enriched.stream().filter(e -> e.activeDealCount() > 0).count();

    // 6. 인메모리 정렬
    List<EnrichedStore> sorted = sortStores(enriched, sort);

    // 7. 페이지 슬라이스
    int fromIdx = page * size;
    if (fromIdx >= sorted.size()) {
      return new StoreListResponse(List.of(), page, size, false, total, dealStoreCount);
    }
    int toIdx = Math.min(fromIdx + size, sorted.size());
    List<StoreListItemResponse> items =
        sorted.subList(fromIdx, toIdx).stream().map(EnrichedStore::toResponse).toList();
    boolean hasNext = toIdx < sorted.size();

    return new StoreListResponse(items, page, size, hasNext, total, dealStoreCount);
  }

  // ── private helpers ──────────────────────────────────────────────────────────────────────────

  private Map<Long, Object[]> buildDealMap(List<Long> storeIds) {
    return clearanceItemRepository
        .findActiveDealSummaryByStoreIds(storeIds, ClearanceItemStatus.OPEN)
        .stream()
        .collect(Collectors.toMap(row -> ((Number) row[0]).longValue(), row -> row));
  }

  private EnrichedStore buildEnrichedStore(
      StoreCandidate c,
      Map<Long, RatingStats> ratingMap,
      Map<Long, Object[]> dealMap,
      Set<Long> favoriteIds) {

    double distanceKm = c.getDistanceMeters() / 1000.0;
    RatingStats rating = ratingMap.getOrDefault(c.getId(), RatingStats.EMPTY);

    Object[] deal = dealMap.get(c.getId());
    long activeDealCount = deal != null ? ((Number) deal[1]).longValue() : 0L;
    double maxDiscountRate = deal != null ? ((BigDecimal) deal[2]).doubleValue() : 0.0;
    LocalDateTime nearestPickupEndAt = deal != null ? (LocalDateTime) deal[3] : null;

    boolean isFavorite = favoriteIds.contains(c.getId());

    return new EnrichedStore(
        c.getId(),
        c.getName(),
        c.getImageUrl(),
        distanceKm,
        rating.average(),
        activeDealCount,
        maxDiscountRate,
        nearestPickupEndAt,
        isFavorite);
  }

  private List<EnrichedStore> sortStores(List<EnrichedStore> stores, StoreSort sort) {
    Comparator<EnrichedStore> comparator =
        switch (sort) {
          case RECOMMENDED ->
              Comparator.comparingDouble(
                      (EnrichedStore e) ->
                          recommendedScore(e.distanceKm(), e.rating(), e.activeDealCount()))
                  .reversed();
          case DISTANCE -> Comparator.comparingDouble(EnrichedStore::distanceKm);
          case DISCOUNT ->
              (a, b) -> {
                boolean aHas = a.activeDealCount() > 0;
                boolean bHas = b.activeDealCount() > 0;
                if (aHas && bHas) return Double.compare(b.maxDiscountRate(), a.maxDiscountRate());
                if (aHas) return -1;
                if (bHas) return 1;
                return 0;
              };
          case CLOSING ->
              (a, b) -> {
                boolean aHas = a.nearestPickupEndAt() != null;
                boolean bHas = b.nearestPickupEndAt() != null;
                if (aHas && bHas) return a.nearestPickupEndAt().compareTo(b.nearestPickupEndAt());
                if (aHas) return -1;
                if (bHas) return 1;
                return 0;
              };
          case RATING ->
              (a, b) -> {
                boolean aHas = a.rating() > 0;
                boolean bHas = b.rating() > 0;
                if (aHas && bHas) return Double.compare(b.rating(), a.rating());
                if (aHas) return -1;
                if (bHas) return 1;
                return 0;
              };
        };
    return stores.stream().sorted(comparator).toList();
  }

  // ── 공유 추천 스코어 공식 (StoreNeighborhoodQueryService 와 동일) ───────────────────────────────

  /**
   * 추천 스코어. StoreNeighborhoodQueryService 와 동일 공식 — 단일 소스.
   *
   * <p>score = (5.0 - distanceKm) + rating + (activeDealCount > 0 ? 1.5 : 0.0)
   */
  public static double recommendedScore(double distanceKm, double rating, long activeDealCount) {
    return (5.0 - distanceKm) + rating + (activeDealCount > 0 ? 1.5 : 0.0);
  }

  // ── 내부 데이터 홀더 ───────────────────────────────────────────────────────────────────────────

  private record EnrichedStore(
      long id,
      String name,
      String imageUrl,
      double distanceKm,
      double rating,
      long activeDealCount,
      double maxDiscountRate,
      LocalDateTime nearestPickupEndAt,
      boolean isFavorite) {

    StoreListItemResponse toResponse() {
      return new StoreListItemResponse(
          id, name, imageUrl, distanceKm, rating, activeDealCount, isFavorite);
    }
  }
}
