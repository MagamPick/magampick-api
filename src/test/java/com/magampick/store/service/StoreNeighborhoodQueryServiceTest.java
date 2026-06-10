package com.magampick.store.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.magampick.address.exception.AddressErrorCode;
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
class StoreNeighborhoodQueryServiceTest {

  @Mock AddressService addressService;
  @Mock StoreRepository storeRepository;
  @Mock ClearanceItemRepository clearanceItemRepository;
  @Mock FavoriteRepository favoriteRepository;
  @Mock ReviewQueryService reviewQueryService;
  @InjectMocks StoreNeighborhoodQueryService service;

  private static final Long CUSTOMER_ID = 1L;
  private static final double LAT = 37.5665;
  private static final double LNG = 126.9780;

  // ── 기본 주소지 없음 ──────────────────────────────────────────────────────────────────────────────

  @Test
  void 기본주소지_없으면_DEFAULT_ADDRESS_REQUIRED() {
    given(addressService.requireDefaultLocation(CUSTOMER_ID))
        .willThrow(new BusinessException(AddressErrorCode.DEFAULT_ADDRESS_REQUIRED));

    assertThatThrownBy(() -> service.getNeighborhoodStores(CUSTOMER_ID))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(AddressErrorCode.DEFAULT_ADDRESS_REQUIRED);
  }

  // ── 빈 결과 ────────────────────────────────────────────────────────────────────────────────────

  @Test
  void 후보_매장_없으면_빈_리스트() {
    stubAddress();
    given(storeRepository.findOpenStoresWithin5km(anyDouble(), anyDouble(), anyString()))
        .willReturn(List.of());

    List<NeighborhoodStoreResponse> result = service.getNeighborhoodStores(CUSTOMER_ID);

    assertThat(result).isEmpty();
  }

  // ── 단골 제외 ─────────────────────────────────────────────────────────────────────────────────

  @Test
  void 단골_매장은_결과에서_제외() {
    stubAddress();
    StoreCandidate favoriteStore = candidate(1L, "단골매장", null, 500.0);
    StoreCandidate normalStore = candidate(2L, "일반매장", null, 1000.0);
    given(storeRepository.findOpenStoresWithin5km(anyDouble(), anyDouble(), anyString()))
        .willReturn(List.of(favoriteStore, normalStore));
    given(reviewQueryService.getStoreRatings(any())).willReturn(Map.of());
    given(
            clearanceItemRepository.findActiveDealSummaryByStoreIds(
                any(), eq(ClearanceItemStatus.OPEN)))
        .willReturn(List.of());
    // 매장1이 단골
    given(favoriteRepository.findStoreIdsByCustomerIdAndStoreIdIn(eq(CUSTOMER_ID), any()))
        .willReturn(List.of(1L));

    List<NeighborhoodStoreResponse> result = service.getNeighborhoodStores(CUSTOMER_ID);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).id()).isEqualTo(2L); // 단골 제외, 일반매장만
  }

  @Test
  void 단골_없으면_전체_포함() {
    stubAddress();
    given(storeRepository.findOpenStoresWithin5km(anyDouble(), anyDouble(), anyString()))
        .willReturn(List.of(candidate(1L, "A", null, 500.0), candidate(2L, "B", null, 1000.0)));
    given(reviewQueryService.getStoreRatings(any())).willReturn(Map.of());
    given(
            clearanceItemRepository.findActiveDealSummaryByStoreIds(
                any(), eq(ClearanceItemStatus.OPEN)))
        .willReturn(List.of());
    given(favoriteRepository.findStoreIdsByCustomerIdAndStoreIdIn(eq(CUSTOMER_ID), any()))
        .willReturn(List.of()); // 단골 없음

    List<NeighborhoodStoreResponse> result = service.getNeighborhoodStores(CUSTOMER_ID);

    assertThat(result).hasSize(2);
  }

  // ── 추천 스코어 정렬 ──────────────────────────────────────────────────────────────────────────

  @Test
  void 추천_스코어_높은_매장이_앞에_온다() {
    stubAddress();
    // storeA: 1km, 평점 4.5, 떨이 있음 → (5-1) + 4.5 + 1.5 = 10.0
    // storeB: 4km, 평점 0.0, 떨이 없음 → (5-4) + 0 + 0 = 1.0
    StoreCandidate a = candidate(1L, "고점수매장", null, 1000.0); // 1km
    StoreCandidate b = candidate(2L, "저점수매장", null, 4000.0); // 4km
    given(storeRepository.findOpenStoresWithin5km(anyDouble(), anyDouble(), anyString()))
        .willReturn(List.of(b, a)); // 역순 넣어도 정렬 후 A 먼저
    given(reviewQueryService.getStoreRatings(any()))
        .willReturn(Map.of(1L, new RatingStats(4.5, 5L)));
    given(
            clearanceItemRepository.findActiveDealSummaryByStoreIds(
                any(), eq(ClearanceItemStatus.OPEN)))
        .willReturn(List.<Object[]>of(dealRow(1L, 2L, 0.30, LocalDateTime.now().plusHours(1))));
    given(favoriteRepository.findStoreIdsByCustomerIdAndStoreIdIn(eq(CUSTOMER_ID), any()))
        .willReturn(List.of());

    List<NeighborhoodStoreResponse> result = service.getNeighborhoodStores(CUSTOMER_ID);

    assertThat(result.get(0).id()).isEqualTo(1L); // 고점수 먼저
    assertThat(result.get(1).id()).isEqualTo(2L);
  }

  // ── top6 상한 ─────────────────────────────────────────────────────────────────────────────────

  @Test
  void top6_최대_6개_반환() {
    stubAddress();
    // 7개 후보 → 6개만 반환
    List<StoreCandidate> candidates =
        List.of(
            candidate(1L, "A", null, 100.0),
            candidate(2L, "B", null, 200.0),
            candidate(3L, "C", null, 300.0),
            candidate(4L, "D", null, 400.0),
            candidate(5L, "E", null, 500.0),
            candidate(6L, "F", null, 600.0),
            candidate(7L, "G", null, 700.0));
    given(storeRepository.findOpenStoresWithin5km(anyDouble(), anyDouble(), anyString()))
        .willReturn(candidates);
    given(reviewQueryService.getStoreRatings(any())).willReturn(Map.of());
    given(
            clearanceItemRepository.findActiveDealSummaryByStoreIds(
                any(), eq(ClearanceItemStatus.OPEN)))
        .willReturn(List.of());
    given(favoriteRepository.findStoreIdsByCustomerIdAndStoreIdIn(eq(CUSTOMER_ID), any()))
        .willReturn(List.of());

    List<NeighborhoodStoreResponse> result = service.getNeighborhoodStores(CUSTOMER_ID);

    assertThat(result).hasSize(6);
  }

  // ── 응답 필드 매핑 ────────────────────────────────────────────────────────────────────────────

  @Test
  void 응답_필드_매핑_정확() {
    stubAddress();
    StoreCandidate c = candidate(10L, "동네빵집", "/img/bread.jpg", 1500.0); // 1.5km
    given(storeRepository.findOpenStoresWithin5km(anyDouble(), anyDouble(), anyString()))
        .willReturn(List.of(c));
    given(reviewQueryService.getStoreRatings(any()))
        .willReturn(Map.of(10L, new RatingStats(4.2, 8L)));
    given(
            clearanceItemRepository.findActiveDealSummaryByStoreIds(
                any(), eq(ClearanceItemStatus.OPEN)))
        .willReturn(List.<Object[]>of(dealRow(10L, 3L, 0.30, LocalDateTime.now().plusHours(2))));
    given(favoriteRepository.findStoreIdsByCustomerIdAndStoreIdIn(eq(CUSTOMER_ID), any()))
        .willReturn(List.of());

    List<NeighborhoodStoreResponse> result = service.getNeighborhoodStores(CUSTOMER_ID);

    assertThat(result).hasSize(1);
    NeighborhoodStoreResponse r = result.get(0);
    assertThat(r.id()).isEqualTo(10L);
    assertThat(r.name()).isEqualTo("동네빵집");
    assertThat(r.imageUrl()).isEqualTo("/img/bread.jpg");
    assertThat(r.distanceKm()).isEqualTo(1.5); // 1500m → 1.5km
    assertThat(r.rating()).isEqualTo(4.2);
    assertThat(r.activeDealCount()).isEqualTo(3L);
  }

  // ── helper ───────────────────────────────────────────────────────────────────────────────────

  private void stubAddress() {
    given(addressService.requireDefaultLocation(CUSTOMER_ID))
        .willReturn(GeometryUtil.toPoint(LAT, LNG));
  }

  private StoreCandidate candidate(Long id, String name, String imageUrl, double distanceMeters) {
    return new StoreCandidate() {
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
      public Double getDistanceMeters() {
        return distanceMeters;
      }
    };
  }

  private Object[] dealRow(
      long storeId, long count, double discountRate, LocalDateTime nearestEnd) {
    return new Object[] {storeId, count, new BigDecimal(String.valueOf(discountRate)), nearestEnd};
  }
}
