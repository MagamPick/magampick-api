package com.magampick.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.magampick.customer.domain.Customer;
import com.magampick.global.exception.BusinessException;
import com.magampick.global.response.SliceResponse;
import com.magampick.order.domain.Order;
import com.magampick.review.domain.Review;
import com.magampick.review.dto.ReviewSummaryResponse;
import com.magampick.review.dto.StoreReviewResponse;
import com.magampick.review.fixture.ReviewFixture;
import com.magampick.review.mapper.ReviewMapper;
import com.magampick.review.repository.ReviewRepository;
import com.magampick.store.domain.Store;
import com.magampick.store.exception.StoreErrorCode;
import com.magampick.store.service.StoreService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReviewQueryServiceTest {

  @Mock ReviewRepository reviewRepository;
  @Mock ReviewMapper reviewMapper;
  @Mock StoreService storeService;
  @InjectMocks ReviewQueryService reviewQueryService;

  private static final Long STORE_ID = 1L;
  private static final Long SELLER_ID = 2L;
  private static final Long CLEARANCE_ITEM_ID = 10L;

  // ── 매장 리뷰 목록 ───────────────────────────────────────────────────────────

  @Test
  void 매장_리뷰_목록_최신순_SliceResponse_반환() {
    // given
    Review review = buildReview(STORE_ID, 4);
    Slice<Review> slice = new SliceImpl<>(List.of(review), PageRequest.of(0, 10), false);
    given(reviewRepository.findByStoreIdOrderByCreatedAtDesc(eq(STORE_ID), any()))
        .willReturn(slice);
    given(reviewMapper.toResponse(review)).willReturn(ReviewFixture.aStoreReviewResponse());

    // when
    SliceResponse<StoreReviewResponse> result =
        reviewQueryService.getStoreReviews(STORE_ID, PageRequest.of(0, 10));

    // then
    assertThat(result.content()).hasSize(1);
    assertThat(result.hasNext()).isFalse();
    assertThat(result.page()).isEqualTo(0);
  }

  @Test
  void 매장_리뷰_없으면_빈_Slice_반환() {
    // given
    Slice<Review> empty = new SliceImpl<>(List.of(), PageRequest.of(0, 10), false);
    given(reviewRepository.findByStoreIdOrderByCreatedAtDesc(eq(STORE_ID), any()))
        .willReturn(empty);

    // when
    SliceResponse<StoreReviewResponse> result =
        reviewQueryService.getStoreReviews(STORE_ID, PageRequest.of(0, 10));

    // then
    assertThat(result.content()).isEmpty();
    assertThat(result.hasNext()).isFalse();
  }

  // ── 사장 본인 매장 리뷰 목록 ──────────────────────────────────────────────────

  @Test
  void 사장_본인매장_리뷰_목록_최신순_반환() {
    // given — 소유권 검증 통과, 리뷰 1건
    Review review = buildReview(STORE_ID, 5);
    given(reviewRepository.findByStoreIdWithCustomerOrderByCreatedAtDesc(STORE_ID))
        .willReturn(List.of(review));
    given(reviewMapper.toResponse(review)).willReturn(ReviewFixture.aStoreReviewResponse());

    // when
    List<StoreReviewResponse> result =
        reviewQueryService.getSellerStoreReviews(SELLER_ID, STORE_ID);

    // then
    assertThat(result).hasSize(1);
  }

  @Test
  void 사장_본인매장_아니면_STORE_ACCESS_DENIED() {
    // given — 소유권 검증 실패
    given(storeService.requireOwnedStore(SELLER_ID, STORE_ID))
        .willThrow(new BusinessException(StoreErrorCode.STORE_ACCESS_DENIED));

    // when & then
    assertThatThrownBy(() -> reviewQueryService.getSellerStoreReviews(SELLER_ID, STORE_ID))
        .isInstanceOf(BusinessException.class);
  }

  // ── 매장 리뷰 요약 ───────────────────────────────────────────────────────────

  @Test
  void 매장_리뷰_요약_평균_및_분포_반환() {
    // given — 별점 4, 5 두 개 → 평균 4.5
    given(reviewRepository.findStoreRatingStats(STORE_ID))
        .willReturn(List.<Object[]>of(new Object[] {4.5, 2L}));
    given(reviewRepository.findRatingDistribution(STORE_ID))
        .willReturn(List.<Object[]>of(new Object[] {5, 1L}, new Object[] {4, 1L}));

    // when
    ReviewSummaryResponse result = reviewQueryService.getReviewSummary(STORE_ID);

    // then
    assertThat(result.average()).isCloseTo(4.5, within(0.01));
    assertThat(result.count()).isEqualTo(2L);
    assertThat(result.distribution()).hasSize(5);
    // 5점 → 1점 순
    assertThat(result.distribution().get(0).star()).isEqualTo(5);
    assertThat(result.distribution().get(0).count()).isEqualTo(1L);
    assertThat(result.distribution().get(1).star()).isEqualTo(4);
    assertThat(result.distribution().get(1).count()).isEqualTo(1L);
    // 3점 이하는 count=0
    assertThat(result.distribution().get(2).count()).isEqualTo(0L);
  }

  @Test
  void 리뷰_없는_매장_요약_평균_0() {
    // given
    given(reviewRepository.findStoreRatingStats(STORE_ID))
        .willReturn(List.<Object[]>of(new Object[] {null, 0L}));
    given(reviewRepository.findRatingDistribution(STORE_ID)).willReturn(List.of());

    // when
    ReviewSummaryResponse result = reviewQueryService.getReviewSummary(STORE_ID);

    // then
    assertThat(result.average()).isEqualTo(0.0);
    assertThat(result.count()).isEqualTo(0L);
    assertThat(result.distribution()).hasSize(5);
    assertThat(result.distribution()).allSatisfy(sc -> assertThat(sc.count()).isEqualTo(0L));
  }

  // ── 매장 평점 집계 (Phase 4 주입용) ──────────────────────────────────────────

  @Test
  void 매장_평점_조회_성공() {
    // given
    given(reviewRepository.findStoreRatingStats(STORE_ID))
        .willReturn(List.<Object[]>of(new Object[] {4.2, 10L}));

    // when
    RatingStats result = reviewQueryService.getStoreRating(STORE_ID);

    // then
    assertThat(result.average()).isCloseTo(4.2, within(0.01));
    assertThat(result.count()).isEqualTo(10L);
  }

  @Test
  void 리뷰_없는_매장_평점_EMPTY() {
    // given
    given(reviewRepository.findStoreRatingStats(STORE_ID))
        .willReturn(List.<Object[]>of(new Object[] {null, 0L}));

    // when
    RatingStats result = reviewQueryService.getStoreRating(STORE_ID);

    // then
    assertThat(result).isEqualTo(RatingStats.EMPTY);
    assertThat(result.average()).isEqualTo(0.0);
    assertThat(result.count()).isEqualTo(0L);
  }

  @Test
  void 매장_배치_평점_조회() {
    // given
    List<Long> ids = List.of(1L, 2L);
    given(reviewRepository.findStoreRatingsStatsBatch(ids))
        .willReturn(List.<Object[]>of(new Object[] {1L, 4.5, 3L}, new Object[] {2L, 3.0, 1L}));

    // when
    Map<Long, RatingStats> result = reviewQueryService.getStoreRatings(ids);

    // then
    assertThat(result).hasSize(2);
    assertThat(result.get(1L).average()).isCloseTo(4.5, within(0.01));
    assertThat(result.get(1L).count()).isEqualTo(3L);
    assertThat(result.get(2L).average()).isCloseTo(3.0, within(0.01));
  }

  @Test
  void 빈_배치_매장_평점_조회_빈_Map_반환() {
    // when
    Map<Long, RatingStats> result = reviewQueryService.getStoreRatings(List.of());

    // then
    assertThat(result).isEmpty();
  }

  // ── 떨이 상품 평점 집계 ───────────────────────────────────────────────────────

  @Test
  void 떨이_평점_조회_성공() {
    // given
    given(reviewRepository.findClearanceItemRatingStats(CLEARANCE_ITEM_ID))
        .willReturn(List.<Object[]>of(new Object[] {4.0, 5L}));

    // when
    RatingStats result = reviewQueryService.getClearanceItemRating(CLEARANCE_ITEM_ID);

    // then
    assertThat(result.average()).isCloseTo(4.0, within(0.01));
    assertThat(result.count()).isEqualTo(5L);
  }

  @Test
  void 떨이_리뷰_없으면_평점_EMPTY() {
    // given
    given(reviewRepository.findClearanceItemRatingStats(CLEARANCE_ITEM_ID))
        .willReturn(List.<Object[]>of(new Object[] {null, 0L}));

    // when
    RatingStats result = reviewQueryService.getClearanceItemRating(CLEARANCE_ITEM_ID);

    // then
    assertThat(result).isEqualTo(RatingStats.EMPTY);
  }

  // ── 내부 헬퍼 ────────────────────────────────────────────────────────────────

  private Review buildReview(Long storeId, int rating) {
    Customer customer =
        Customer.builder().email("c@test.com").passwordHash("h").nickname("테스터").build();
    Store store =
        Store.builder()
            .seller(null)
            .businessNumber("1234567890")
            .representativeName("홍길동")
            .openDate(LocalDate.of(2024, 3, 15))
            .name("매장")
            .roadAddress("서울 강남구")
            .zonecode("06158")
            .location(null)
            .phone("0212345678")
            .build();
    ReflectionTestUtils.setField(store, "id", storeId);
    Order order = Order.builder().customer(customer).store(store).build();
    return Review.builder()
        .customer(customer)
        .order(order)
        .store(store)
        .rating(rating)
        .content("테스트")
        .build();
  }
}
