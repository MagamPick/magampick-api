package com.magampick.store.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StoreMapQueryServiceTest {

  @Mock StoreRepository storeRepository;
  @Mock ClearanceItemRepository clearanceItemRepository;
  @Mock ReviewQueryService reviewQueryService;
  @InjectMocks StoreMapQueryService storeMapQueryService;

  private static final double LAT = 37.5665;
  private static final double LNG = 126.9780;

  // ── radiusKm 검증 ─────────────────────────────────────────────────────────────────────────────

  @Test
  void radiusKm_허용값_아닌_경우_예외() {
    assertThatThrownBy(() -> storeMapQueryService.getMapStores(LAT, LNG, 2, false))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(CommonErrorCode.INVALID_INPUT);
  }

  @Test
  void radiusKm_4_예외() {
    assertThatThrownBy(() -> storeMapQueryService.getMapStores(LAT, LNG, 4, false))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(CommonErrorCode.INVALID_INPUT);
  }

  @Test
  void radiusKm_0_예외() {
    assertThatThrownBy(() -> storeMapQueryService.getMapStores(LAT, LNG, 0, false))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(CommonErrorCode.INVALID_INPUT);
  }

  // ── 빈 결과 ────────────────────────────────────────────────────────────────────────────────────

  @Test
  void 후보_없으면_빈_목록_반환() {
    given(
            storeRepository.findMapStoresWithinRadius(
                anyDouble(), anyDouble(), anyInt(), anyString()))
        .willReturn(List.of());

    List<MapStoreResponse> result = storeMapQueryService.getMapStores(LAT, LNG, 3, false);

    assertThat(result).isEmpty();
  }

  // ── 평점·떨이 enrich ───────────────────────────────────────────────────────────────────────────

  @Test
  void 평점_enrich_정확() {
    MapStoreCandidate c = candidate(10L, "빵집", "/img/a.jpg", 37.57, 126.98, 1000.0);
    given(
            storeRepository.findMapStoresWithinRadius(
                anyDouble(), anyDouble(), anyInt(), anyString()))
        .willReturn(List.of(c));
    given(reviewQueryService.getStoreRatings(any()))
        .willReturn(Map.of(10L, new RatingStats(4.5, 10L)));
    given(
            clearanceItemRepository.findActiveDealSummaryByStoreIds(
                any(), eq(ClearanceItemStatus.OPEN)))
        .willReturn(List.of());

    List<MapStoreResponse> result = storeMapQueryService.getMapStores(LAT, LNG, 3, false);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).rating()).isEqualTo(4.5);
  }

  @Test
  void 떨이_없는_매장은_activeDealCount_0_maxDiscountRate_0() {
    MapStoreCandidate c = candidate(10L, "빵집", null, 37.57, 126.98, 500.0);
    given(
            storeRepository.findMapStoresWithinRadius(
                anyDouble(), anyDouble(), anyInt(), anyString()))
        .willReturn(List.of(c));
    given(reviewQueryService.getStoreRatings(any())).willReturn(Map.of());
    given(
            clearanceItemRepository.findActiveDealSummaryByStoreIds(
                any(), eq(ClearanceItemStatus.OPEN)))
        .willReturn(List.of());

    List<MapStoreResponse> result = storeMapQueryService.getMapStores(LAT, LNG, 3, false);

    assertThat(result.get(0).activeDealCount()).isZero();
    assertThat(result.get(0).maxDiscountRate()).isZero();
  }

  @Test
  void 떨이_있는_매장_activeDealCount_maxDiscountRate_enrich() {
    MapStoreCandidate c = candidate(10L, "빵집", "/img/a.jpg", 37.57, 126.98, 2000.0);
    given(
            storeRepository.findMapStoresWithinRadius(
                anyDouble(), anyDouble(), anyInt(), anyString()))
        .willReturn(List.of(c));
    given(reviewQueryService.getStoreRatings(any())).willReturn(Map.of());
    given(
            clearanceItemRepository.findActiveDealSummaryByStoreIds(
                any(), eq(ClearanceItemStatus.OPEN)))
        .willReturn(
            List.<Object[]>of(
                dealRow(10L, 3L, new BigDecimal("0.35"), LocalDateTime.now().plusHours(1))));

    List<MapStoreResponse> result = storeMapQueryService.getMapStores(LAT, LNG, 3, false);

    assertThat(result.get(0).activeDealCount()).isEqualTo(3L);
    assertThat(result.get(0).maxDiscountRate()).isEqualTo(35); // 0.35 * 100 = 35
  }

  @Test
  void maxDiscountRate_반올림_정확() {
    // 0.333... → 33%
    MapStoreCandidate c = candidate(10L, "A", null, 37.57, 126.98, 500.0);
    given(
            storeRepository.findMapStoresWithinRadius(
                anyDouble(), anyDouble(), anyInt(), anyString()))
        .willReturn(List.of(c));
    given(reviewQueryService.getStoreRatings(any())).willReturn(Map.of());
    given(
            clearanceItemRepository.findActiveDealSummaryByStoreIds(
                any(), eq(ClearanceItemStatus.OPEN)))
        .willReturn(
            List.<Object[]>of(
                dealRow(10L, 1L, new BigDecimal("0.3333"), LocalDateTime.now().plusHours(1))));

    List<MapStoreResponse> result = storeMapQueryService.getMapStores(LAT, LNG, 3, false);

    assertThat(result.get(0).maxDiscountRate()).isEqualTo(33);
  }

  // ── distanceKm 변환 ────────────────────────────────────────────────────────────────────────────

  @Test
  void distanceMeters_를_km으로_변환() {
    MapStoreCandidate c = candidate(10L, "빵집", null, 37.57, 126.98, 2500.0); // 2500m → 2.5km
    given(
            storeRepository.findMapStoresWithinRadius(
                anyDouble(), anyDouble(), anyInt(), anyString()))
        .willReturn(List.of(c));
    given(reviewQueryService.getStoreRatings(any())).willReturn(Map.of());
    given(
            clearanceItemRepository.findActiveDealSummaryByStoreIds(
                any(), eq(ClearanceItemStatus.OPEN)))
        .willReturn(List.of());

    List<MapStoreResponse> result = storeMapQueryService.getMapStores(LAT, LNG, 3, false);

    assertThat(result.get(0).distanceKm()).isEqualTo(2.5);
  }

  // ── dealsOnly 필터 ────────────────────────────────────────────────────────────────────────────

  @Test
  void dealsOnly_true_떨이없는_매장_제외() {
    MapStoreCandidate withDeal = candidate(1L, "떨이있음", null, 37.57, 126.98, 1000.0);
    MapStoreCandidate noDeal = candidate(2L, "떨이없음", null, 37.56, 126.97, 500.0);
    given(
            storeRepository.findMapStoresWithinRadius(
                anyDouble(), anyDouble(), anyInt(), anyString()))
        .willReturn(List.of(withDeal, noDeal));
    given(reviewQueryService.getStoreRatings(any())).willReturn(Map.of());
    given(
            clearanceItemRepository.findActiveDealSummaryByStoreIds(
                any(), eq(ClearanceItemStatus.OPEN)))
        .willReturn(
            List.<Object[]>of(
                dealRow(1L, 2L, new BigDecimal("0.30"), LocalDateTime.now().plusHours(1))));

    List<MapStoreResponse> result = storeMapQueryService.getMapStores(LAT, LNG, 3, true);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).id()).isEqualTo(1L);
  }

  @Test
  void dealsOnly_false_전체_매장_반환() {
    MapStoreCandidate withDeal = candidate(1L, "떨이있음", null, 37.57, 126.98, 1000.0);
    MapStoreCandidate noDeal = candidate(2L, "떨이없음", null, 37.56, 126.97, 500.0);
    given(
            storeRepository.findMapStoresWithinRadius(
                anyDouble(), anyDouble(), anyInt(), anyString()))
        .willReturn(List.of(withDeal, noDeal));
    given(reviewQueryService.getStoreRatings(any())).willReturn(Map.of());
    given(
            clearanceItemRepository.findActiveDealSummaryByStoreIds(
                any(), eq(ClearanceItemStatus.OPEN)))
        .willReturn(
            List.<Object[]>of(
                dealRow(1L, 2L, new BigDecimal("0.30"), LocalDateTime.now().plusHours(1))));

    List<MapStoreResponse> result = storeMapQueryService.getMapStores(LAT, LNG, 3, false);

    assertThat(result).hasSize(2);
  }

  @Test
  void radiusKm_1_3_5_허용() {
    for (int radius : new int[] {1, 3, 5}) {
      given(
              storeRepository.findMapStoresWithinRadius(
                  anyDouble(), anyDouble(), eq(radius * 1000), anyString()))
          .willReturn(List.of());

      List<MapStoreResponse> result = storeMapQueryService.getMapStores(LAT, LNG, radius, false);

      assertThat(result).isEmpty();
    }
  }

  // ── lat/lng projection ────────────────────────────────────────────────────────────────────────

  @Test
  void 위경도_응답에_포함() {
    double storeLat = 37.5700;
    double storeLng = 126.9850;
    MapStoreCandidate c = candidate(10L, "매장", null, storeLat, storeLng, 1000.0);
    given(
            storeRepository.findMapStoresWithinRadius(
                anyDouble(), anyDouble(), anyInt(), anyString()))
        .willReturn(List.of(c));
    given(reviewQueryService.getStoreRatings(any())).willReturn(Map.of());
    given(
            clearanceItemRepository.findActiveDealSummaryByStoreIds(
                any(), eq(ClearanceItemStatus.OPEN)))
        .willReturn(List.of());

    List<MapStoreResponse> result = storeMapQueryService.getMapStores(LAT, LNG, 3, false);

    assertThat(result.get(0).latitude()).isEqualTo(storeLat);
    assertThat(result.get(0).longitude()).isEqualTo(storeLng);
  }

  // ── helper ────────────────────────────────────────────────────────────────────────────────────

  private MapStoreCandidate candidate(
      Long id, String name, String imageUrl, double lat, double lng, double distanceMeters) {
    return new MapStoreCandidate() {
      @Override
      public Long getId() {
        return id;
      }

      @Override
      public String getName() {
        return name;
      }

      @Override
      public String getImageUrl() {
        return imageUrl;
      }

      @Override
      public Double getLatitude() {
        return lat;
      }

      @Override
      public Double getLongitude() {
        return lng;
      }

      @Override
      public Double getDistanceMeters() {
        return distanceMeters;
      }
    };
  }

  private Object[] dealRow(
      long storeId, long count, BigDecimal discountRate, LocalDateTime nearestEnd) {
    return new Object[] {storeId, count, discountRate, nearestEnd};
  }
}
