package com.magampick.store.service;

import com.magampick.clearance.domain.ClearanceItemStatus;
import com.magampick.clearance.repository.ClearanceItemRepository;
import com.magampick.global.exception.BusinessException;
import com.magampick.global.exception.CommonErrorCode;
import com.magampick.review.service.RatingStats;
import com.magampick.review.service.ReviewQueryService;
import com.magampick.store.dto.MapStoreResponse;
import com.magampick.store.repository.MapStoreCandidate;
import com.magampick.store.repository.StoreRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 지도 기반 매장 조회 서비스. 전달받은 GPS 좌표(lat/lng) 기준 radiusKm 이내 OPEN 매장 마커 목록. PostGIS 후보 추출 → 배치
 * enrich(평점·떨이) → dealsOnly 필터 → 매핑.
 *
 * <p>radiusKm: 1/3/5 만 허용. 그 외 400 (INVALID_INPUT). 정렬·페이징 없음 (마커 표시용 전체 목록).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreMapQueryService {

  private static final Set<Integer> ALLOWED_RADIUS_KM = Set.of(1, 3, 5);

  private final StoreRepository storeRepository;
  private final ClearanceItemRepository clearanceItemRepository;
  private final ReviewQueryService reviewQueryService;

  /**
   * 지도용 매장 마커 목록 조회.
   *
   * @param lat origin 위도 (카카오맵 중심 GPS)
   * @param lng origin 경도 (카카오맵 중심 GPS)
   * @param radiusKm 반경(km). 1/3/5 만 허용 — 그 외 {@link CommonErrorCode#INVALID_INPUT}
   * @param dealsOnly true=활성 떨이 있는 매장만, false=전체
   * @return 반경 내 OPEN·오늘영업 매장 마커 목록
   */
  public List<MapStoreResponse> getMapStores(
      double lat, double lng, int radiusKm, boolean dealsOnly) {
    // 반경 유효성 검증
    if (!ALLOWED_RADIUS_KM.contains(radiusKm)) {
      throw new BusinessException(CommonErrorCode.INVALID_INPUT);
    }

    // 오늘 요일 · 반경 계산
    String today = LocalDate.now().getDayOfWeek().name();
    int radiusMeters = radiusKm * 1000;

    // PostGIS 후보 쿼리
    List<MapStoreCandidate> candidates =
        storeRepository.findMapStoresWithinRadius(lat, lng, radiusMeters, today);

    if (candidates.isEmpty()) {
      return List.of();
    }

    List<Long> storeIds = candidates.stream().map(MapStoreCandidate::getId).toList();

    // 배치 enrich (N+1 회피)
    Map<Long, RatingStats> ratingMap = reviewQueryService.getStoreRatings(storeIds);
    Map<Long, Object[]> dealMap = buildDealMap(storeIds);

    // dealsOnly 필터 · 응답 매핑
    return candidates.stream()
        .map(c -> toResponse(c, ratingMap, dealMap))
        .filter(r -> !dealsOnly || r.activeDealCount() > 0)
        .toList();
  }

  // ── private helpers ──────────────────────────────────────────────────────────────────────────

  private Map<Long, Object[]> buildDealMap(List<Long> storeIds) {
    return clearanceItemRepository
        .findActiveDealSummaryByStoreIds(storeIds, ClearanceItemStatus.OPEN)
        .stream()
        .collect(Collectors.toMap(row -> ((Number) row[0]).longValue(), row -> row));
  }

  private MapStoreResponse toResponse(
      MapStoreCandidate c, Map<Long, RatingStats> ratingMap, Map<Long, Object[]> dealMap) {

    double distanceKm = c.getDistanceMeters() / 1000.0;
    RatingStats rating = ratingMap.getOrDefault(c.getId(), RatingStats.EMPTY);

    Object[] deal = dealMap.get(c.getId());
    long activeDealCount = deal != null ? ((Number) deal[1]).longValue() : 0L;
    int maxDiscountRate =
        deal != null
            ? ((BigDecimal) deal[2])
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .intValue()
            : 0;

    return new MapStoreResponse(
        c.getId(),
        c.getName(),
        c.getImageUrl(),
        c.getLatitude(),
        c.getLongitude(),
        distanceKm,
        rating.average(),
        activeDealCount,
        maxDiscountRate);
  }
}
