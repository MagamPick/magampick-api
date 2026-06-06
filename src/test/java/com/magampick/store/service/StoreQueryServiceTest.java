package com.magampick.store.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StoreQueryServiceTest {

  @Mock AddressRepository addressRepository;
  @Mock StoreRepository storeRepository;
  @Mock ClearanceItemRepository clearanceItemRepository;
  @Mock FavoriteRepository favoriteRepository;
  @Mock ReviewQueryService reviewQueryService;
  @InjectMocks StoreQueryService storeQueryService;

  private static final Long CUSTOMER_ID = 1L;
  private static final double LAT = 37.5665;
  private static final double LNG = 126.9780;

  // ── 기본 주소지 ───────────────────────────────────────────────────────────────────────────────

  @Test
  void 기본주소지_없으면_예외() {
    given(addressRepository.findByCustomerIdAndIsDefaultTrue(CUSTOMER_ID))
        .willReturn(Optional.empty());

    assertThatThrownBy(() -> storeQueryService.getStores(CUSTOMER_ID, StoreSort.RECOMMENDED, 0, 20))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(AddressErrorCode.DEFAULT_ADDRESS_REQUIRED);
  }

  // ── 빈 결과 ────────────────────────────────────────────────────────────────────────────────────

  @Test
  void 후보_매장_없으면_빈_응답() {
    stubDefaultAddress();
    given(storeRepository.findOpenStoresWithin5km(anyDouble(), anyDouble(), anyString()))
        .willReturn(List.of());

    StoreListResponse response =
        storeQueryService.getStores(CUSTOMER_ID, StoreSort.RECOMMENDED, 0, 20);

    assertThat(response.items()).isEmpty();
    assertThat(response.total()).isZero();
    assertThat(response.dealStoreCount()).isZero();
    assertThat(response.hasNext()).isFalse();
  }

  // ── 집계 조립 ──────────────────────────────────────────────────────────────────────────────────

  @Test
  void 응답_아이템에_평점_단골_떨이수_포함() {
    stubDefaultAddress();
    StoreCandidate candidateA = candidate(10L, "매장A", "/img/a.jpg", 1000.0); // 1km
    given(storeRepository.findOpenStoresWithin5km(anyDouble(), anyDouble(), anyString()))
        .willReturn(List.of(candidateA));
    given(reviewQueryService.getStoreRatings(any()))
        .willReturn(Map.of(10L, new RatingStats(4.5, 10L)));
    given(
            clearanceItemRepository.findActiveDealSummaryByStoreIds(
                any(), eq(ClearanceItemStatus.OPEN)))
        .willReturn(List.<Object[]>of(dealRow(10L, 2L, 0.35, LocalDateTime.now().plusHours(2))));
    given(favoriteRepository.findStoreIdsByCustomerIdAndStoreIdIn(eq(CUSTOMER_ID), any()))
        .willReturn(List.of(10L));

    StoreListResponse response =
        storeQueryService.getStores(CUSTOMER_ID, StoreSort.RECOMMENDED, 0, 20);

    assertThat(response.items()).hasSize(1);
    StoreListItemResponse item = response.items().get(0);
    assertThat(item.id()).isEqualTo(10L);
    assertThat(item.rating()).isEqualTo(4.5);
    assertThat(item.activeDealCount()).isEqualTo(2L);
    assertThat(item.isFavorite()).isTrue();
    assertThat(item.distanceKm()).isEqualTo(1.0); // 1000m → 1km
    assertThat(response.dealStoreCount()).isEqualTo(1L);
  }

  @Test
  void 떨이_없는_매장은_dealStoreCount_에_포함_안됨() {
    stubDefaultAddress();
    given(storeRepository.findOpenStoresWithin5km(anyDouble(), anyDouble(), anyString()))
        .willReturn(List.of(candidate(10L, "A", null, 500.0)));
    given(reviewQueryService.getStoreRatings(any())).willReturn(Map.of());
    given(
            clearanceItemRepository.findActiveDealSummaryByStoreIds(
                any(), eq(ClearanceItemStatus.OPEN)))
        .willReturn(List.of()); // 떨이 없음
    given(favoriteRepository.findStoreIdsByCustomerIdAndStoreIdIn(any(), any()))
        .willReturn(List.of());

    StoreListResponse response =
        storeQueryService.getStores(CUSTOMER_ID, StoreSort.RECOMMENDED, 0, 20);

    assertThat(response.dealStoreCount()).isZero();
  }

  // ── 정렬: 추천 ─────────────────────────────────────────────────────────────────────────────────

  @Test
  void 추천_정렬_점수_높은_매장이_앞에_온다() {
    stubDefaultAddress();
    // storeA: 1km, 평점 4.5, 떨이 있음 → (5-1) + 4.5 + 1.5 = 10.0
    // storeB: 4km, 평점 0.0, 떨이 없음 → (5-4) + 0 + 0 = 1.0
    StoreCandidate a = candidate(1L, "storeA", null, 1000.0);
    StoreCandidate b = candidate(2L, "storeB", null, 4000.0);
    given(storeRepository.findOpenStoresWithin5km(anyDouble(), anyDouble(), anyString()))
        .willReturn(List.of(b, a)); // 역순으로 넣어도 정렬 후 A가 먼저
    given(reviewQueryService.getStoreRatings(any()))
        .willReturn(Map.of(1L, new RatingStats(4.5, 5L)));
    given(
            clearanceItemRepository.findActiveDealSummaryByStoreIds(
                any(), eq(ClearanceItemStatus.OPEN)))
        .willReturn(List.<Object[]>of(dealRow(1L, 1L, 0.30, LocalDateTime.now().plusHours(1))));
    given(favoriteRepository.findStoreIdsByCustomerIdAndStoreIdIn(any(), any()))
        .willReturn(List.of());

    StoreListResponse response =
        storeQueryService.getStores(CUSTOMER_ID, StoreSort.RECOMMENDED, 0, 20);

    assertThat(response.items().get(0).id()).isEqualTo(1L); // storeA 먼저
    assertThat(response.items().get(1).id()).isEqualTo(2L);
  }

  // ── 정렬: 거리 ─────────────────────────────────────────────────────────────────────────────────

  @Test
  void 거리_정렬_가까운_매장이_앞에_온다() {
    stubDefaultAddress();
    StoreCandidate near = candidate(1L, "근처", null, 500.0); // 0.5km
    StoreCandidate far = candidate(2L, "먼곳", null, 3000.0); // 3km
    given(storeRepository.findOpenStoresWithin5km(anyDouble(), anyDouble(), anyString()))
        .willReturn(List.of(far, near)); // 역순
    given(reviewQueryService.getStoreRatings(any())).willReturn(Map.of());
    given(
            clearanceItemRepository.findActiveDealSummaryByStoreIds(
                any(), eq(ClearanceItemStatus.OPEN)))
        .willReturn(List.of());
    given(favoriteRepository.findStoreIdsByCustomerIdAndStoreIdIn(any(), any()))
        .willReturn(List.of());

    StoreListResponse response =
        storeQueryService.getStores(CUSTOMER_ID, StoreSort.DISTANCE, 0, 20);

    assertThat(response.items().get(0).id()).isEqualTo(1L); // near 먼저
  }

  // ── 정렬: 할인율 ────────────────────────────────────────────────────────────────────────────────

  @Test
  void 할인율_정렬_떨이_없는_매장은_뒤로() {
    stubDefaultAddress();
    StoreCandidate withDeal = candidate(1L, "떨이있음", null, 1000.0);
    StoreCandidate noDeal = candidate(2L, "떨이없음", null, 500.0); // 더 가깝지만 떨이 없음
    given(storeRepository.findOpenStoresWithin5km(anyDouble(), anyDouble(), anyString()))
        .willReturn(List.of(noDeal, withDeal));
    given(reviewQueryService.getStoreRatings(any())).willReturn(Map.of());
    given(
            clearanceItemRepository.findActiveDealSummaryByStoreIds(
                any(), eq(ClearanceItemStatus.OPEN)))
        .willReturn(List.<Object[]>of(dealRow(1L, 2L, 0.40, LocalDateTime.now().plusHours(1))));
    given(favoriteRepository.findStoreIdsByCustomerIdAndStoreIdIn(any(), any()))
        .willReturn(List.of());

    StoreListResponse response =
        storeQueryService.getStores(CUSTOMER_ID, StoreSort.DISCOUNT, 0, 20);

    assertThat(response.items().get(0).id()).isEqualTo(1L); // 떨이 있는 매장 먼저
    assertThat(response.items().get(1).id()).isEqualTo(2L); // 떨이 없는 매장 뒤
  }

  @Test
  void 할인율_정렬_할인율_높은_순() {
    stubDefaultAddress();
    StoreCandidate low = candidate(1L, "할인낮음", null, 1000.0);
    StoreCandidate high = candidate(2L, "할인높음", null, 1500.0);
    given(storeRepository.findOpenStoresWithin5km(anyDouble(), anyDouble(), anyString()))
        .willReturn(List.of(low, high));
    given(reviewQueryService.getStoreRatings(any())).willReturn(Map.of());
    given(
            clearanceItemRepository.findActiveDealSummaryByStoreIds(
                any(), eq(ClearanceItemStatus.OPEN)))
        .willReturn(
            List.of(
                dealRow(1L, 1L, 0.20, LocalDateTime.now().plusHours(1)), // 20%
                dealRow(2L, 1L, 0.50, LocalDateTime.now().plusHours(1)))); // 50%
    given(favoriteRepository.findStoreIdsByCustomerIdAndStoreIdIn(any(), any()))
        .willReturn(List.of());

    StoreListResponse response =
        storeQueryService.getStores(CUSTOMER_ID, StoreSort.DISCOUNT, 0, 20);

    assertThat(response.items().get(0).id()).isEqualTo(2L); // 50% 먼저
    assertThat(response.items().get(1).id()).isEqualTo(1L);
  }

  // ── 정렬: 마감 임박 ────────────────────────────────────────────────────────────────────────────

  @Test
  void 마감임박_정렬_떨이_없는_매장은_뒤로() {
    stubDefaultAddress();
    StoreCandidate withDeal = candidate(1L, "떨이있음", null, 1000.0);
    StoreCandidate noDeal = candidate(2L, "떨이없음", null, 500.0);
    given(storeRepository.findOpenStoresWithin5km(anyDouble(), anyDouble(), anyString()))
        .willReturn(List.of(noDeal, withDeal));
    given(reviewQueryService.getStoreRatings(any())).willReturn(Map.of());
    given(
            clearanceItemRepository.findActiveDealSummaryByStoreIds(
                any(), eq(ClearanceItemStatus.OPEN)))
        .willReturn(List.<Object[]>of(dealRow(1L, 1L, 0.30, LocalDateTime.now().plusHours(3))));
    given(favoriteRepository.findStoreIdsByCustomerIdAndStoreIdIn(any(), any()))
        .willReturn(List.of());

    StoreListResponse response = storeQueryService.getStores(CUSTOMER_ID, StoreSort.CLOSING, 0, 20);

    assertThat(response.items().get(0).id()).isEqualTo(1L); // 떨이 있는 매장 먼저
    assertThat(response.items().get(1).id()).isEqualTo(2L);
  }

  @Test
  void 마감임박_정렬_빠른_마감시간_순() {
    stubDefaultAddress();
    StoreCandidate later = candidate(1L, "늦게마감", null, 1000.0);
    StoreCandidate sooner = candidate(2L, "빨리마감", null, 1500.0);
    LocalDateTime soon = LocalDateTime.now().plusHours(1);
    LocalDateTime late = LocalDateTime.now().plusHours(5);
    given(storeRepository.findOpenStoresWithin5km(anyDouble(), anyDouble(), anyString()))
        .willReturn(List.of(later, sooner));
    given(reviewQueryService.getStoreRatings(any())).willReturn(Map.of());
    given(
            clearanceItemRepository.findActiveDealSummaryByStoreIds(
                any(), eq(ClearanceItemStatus.OPEN)))
        .willReturn(List.<Object[]>of(dealRow(1L, 1L, 0.20, late), dealRow(2L, 1L, 0.20, soon)));
    given(favoriteRepository.findStoreIdsByCustomerIdAndStoreIdIn(any(), any()))
        .willReturn(List.of());

    StoreListResponse response = storeQueryService.getStores(CUSTOMER_ID, StoreSort.CLOSING, 0, 20);

    assertThat(response.items().get(0).id()).isEqualTo(2L); // 빨리 마감 먼저
  }

  // ── 정렬: 별점 ─────────────────────────────────────────────────────────────────────────────────

  @Test
  void 별점_정렬_리뷰_없는_매장은_뒤로() {
    stubDefaultAddress();
    StoreCandidate withRating = candidate(1L, "리뷰있음", null, 1000.0);
    StoreCandidate noRating = candidate(2L, "리뷰없음", null, 500.0);
    given(storeRepository.findOpenStoresWithin5km(anyDouble(), anyDouble(), anyString()))
        .willReturn(List.of(noRating, withRating));
    given(reviewQueryService.getStoreRatings(any()))
        .willReturn(Map.of(1L, new RatingStats(4.0, 3L)));
    given(
            clearanceItemRepository.findActiveDealSummaryByStoreIds(
                any(), eq(ClearanceItemStatus.OPEN)))
        .willReturn(List.of());
    given(favoriteRepository.findStoreIdsByCustomerIdAndStoreIdIn(any(), any()))
        .willReturn(List.of());

    StoreListResponse response = storeQueryService.getStores(CUSTOMER_ID, StoreSort.RATING, 0, 20);

    assertThat(response.items().get(0).id()).isEqualTo(1L); // 리뷰 있는 먼저
    assertThat(response.items().get(1).id()).isEqualTo(2L);
  }

  @Test
  void 별점_정렬_높은_별점_순() {
    stubDefaultAddress();
    StoreCandidate low = candidate(1L, "별점낮음", null, 1000.0);
    StoreCandidate high = candidate(2L, "별점높음", null, 1500.0);
    given(storeRepository.findOpenStoresWithin5km(anyDouble(), anyDouble(), anyString()))
        .willReturn(List.of(low, high));
    given(reviewQueryService.getStoreRatings(any()))
        .willReturn(Map.of(1L, new RatingStats(3.0, 5L), 2L, new RatingStats(4.8, 10L)));
    given(
            clearanceItemRepository.findActiveDealSummaryByStoreIds(
                any(), eq(ClearanceItemStatus.OPEN)))
        .willReturn(List.of());
    given(favoriteRepository.findStoreIdsByCustomerIdAndStoreIdIn(any(), any()))
        .willReturn(List.of());

    StoreListResponse response = storeQueryService.getStores(CUSTOMER_ID, StoreSort.RATING, 0, 20);

    assertThat(response.items().get(0).id()).isEqualTo(2L); // 4.8 먼저
  }

  // ── 페이징 ─────────────────────────────────────────────────────────────────────────────────────

  @Test
  void 페이징_hasNext_정확() {
    stubDefaultAddress();
    // 3개 후보, size=2 → hasNext=true
    given(storeRepository.findOpenStoresWithin5km(anyDouble(), anyDouble(), anyString()))
        .willReturn(
            List.of(
                candidate(1L, "A", null, 100.0),
                candidate(2L, "B", null, 200.0),
                candidate(3L, "C", null, 300.0)));
    given(reviewQueryService.getStoreRatings(any())).willReturn(Map.of());
    given(
            clearanceItemRepository.findActiveDealSummaryByStoreIds(
                any(), eq(ClearanceItemStatus.OPEN)))
        .willReturn(List.of());
    given(favoriteRepository.findStoreIdsByCustomerIdAndStoreIdIn(any(), any()))
        .willReturn(List.of());

    StoreListResponse response = storeQueryService.getStores(CUSTOMER_ID, StoreSort.DISTANCE, 0, 2);

    assertThat(response.items()).hasSize(2);
    assertThat(response.hasNext()).isTrue();
    assertThat(response.total()).isEqualTo(3L);
  }

  @Test
  void 페이지_범위_초과시_빈_응답() {
    stubDefaultAddress();
    given(storeRepository.findOpenStoresWithin5km(anyDouble(), anyDouble(), anyString()))
        .willReturn(List.of(candidate(1L, "A", null, 100.0)));
    given(reviewQueryService.getStoreRatings(any())).willReturn(Map.of());
    given(
            clearanceItemRepository.findActiveDealSummaryByStoreIds(
                any(), eq(ClearanceItemStatus.OPEN)))
        .willReturn(List.of());
    given(favoriteRepository.findStoreIdsByCustomerIdAndStoreIdIn(any(), any()))
        .willReturn(List.of());

    StoreListResponse response =
        storeQueryService.getStores(CUSTOMER_ID, StoreSort.DISTANCE, 5, 20);

    assertThat(response.items()).isEmpty();
    assertThat(response.hasNext()).isFalse();
    assertThat(response.total()).isEqualTo(1L); // total 은 전체 후보 수
  }

  // ── helper ───────────────────────────────────────────────────────────────────────────────────

  private void stubDefaultAddress() {
    Address address = makeAddress(LAT, LNG);
    given(addressRepository.findByCustomerIdAndIsDefaultTrue(CUSTOMER_ID))
        .willReturn(Optional.of(address));
  }

  private Address makeAddress(double lat, double lng) {
    Address address =
        Address.builder()
            .customer(null) // Mockito 테스트 — customer 참조 불필요
            .label("집")
            .roadAddress("서울시 중구 테스트로 1")
            .zonecode("04524")
            .location(GeometryUtil.toPoint(lat, lng))
            .isDefault(true)
            .build();
    return address;
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

  /**
   * Object[] 배열 생성: [storeId, activeDealCount, maxDiscountRate, nearestPickupEndAt]. JPQL GROUP BY
   * 결과 타입 모방.
   */
  private Object[] dealRow(
      long storeId, long count, double discountRate, LocalDateTime nearestEnd) {
    return new Object[] {storeId, count, new BigDecimal(String.valueOf(discountRate)), nearestEnd};
  }
}
