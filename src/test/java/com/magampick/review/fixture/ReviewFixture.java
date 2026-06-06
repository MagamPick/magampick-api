package com.magampick.review.fixture;

import com.magampick.customer.domain.Customer;
import com.magampick.order.domain.Order;
import com.magampick.review.domain.Review;
import com.magampick.review.domain.ReviewImage;
import com.magampick.review.domain.ReviewReply;
import com.magampick.review.domain.ReviewTag;
import com.magampick.review.dto.ReviewSummaryResponse;
import com.magampick.review.dto.StoreReviewResponse;
import com.magampick.seller.domain.Seller;
import com.magampick.store.domain.Store;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

public class ReviewFixture {

  private ReviewFixture() {}

  /** 기본 리뷰 (별점 4, 내용 있음). */
  public static Review aReview(Customer customer, Order order, Store store) {
    return Review.builder()
        .customer(customer)
        .order(order)
        .store(store)
        .rating(4)
        .content("맛있어요!")
        .build();
  }

  /** 지정 별점 리뷰. */
  public static Review aReviewWithRating(Customer customer, Order order, Store store, int rating) {
    return Review.builder()
        .customer(customer)
        .order(order)
        .store(store)
        .rating(rating)
        .content("리뷰 내용")
        .build();
  }

  /** 이미지가 포함된 리뷰 이미지 엔티티. */
  public static ReviewImage aReviewImage(Review review, String url, int sortOrder) {
    return ReviewImage.builder().review(review).url(url).sortOrder(sortOrder).build();
  }

  /** 사장 답글. */
  public static ReviewReply aReviewReply(Review review, Seller seller) {
    return ReviewReply.builder().review(review).seller(seller).content("감사합니다!").build();
  }

  // ── Controller 슬라이스 테스트용 응답 픽스처 ────────────────────────────────────

  /** 목록 응답 픽스처 (mock 용). */
  public static StoreReviewResponse aStoreReviewResponse() {
    return new StoreReviewResponse(
        1L,
        "테스터",
        4,
        "맛있어요!",
        OffsetDateTime.now(ZoneOffset.ofHours(9)),
        List.of(new StoreReviewResponse.ReviewedProduct(10L, "deal", "크로아상")),
        List.of("/uploads/review/photo1.jpg"),
        List.of(ReviewTag.FRESH.getLabel(), ReviewTag.KIND.getLabel()),
        null);
  }

  /** 요약 응답 픽스처 (mock 용). */
  public static ReviewSummaryResponse aReviewSummaryResponse() {
    return new ReviewSummaryResponse(
        4.2,
        5L,
        List.of(
            new ReviewSummaryResponse.StarCount(5, 2),
            new ReviewSummaryResponse.StarCount(4, 2),
            new ReviewSummaryResponse.StarCount(3, 1),
            new ReviewSummaryResponse.StarCount(2, 0),
            new ReviewSummaryResponse.StarCount(1, 0)));
  }

  /** 빈 요약 응답 픽스처 (리뷰 없는 매장). */
  public static ReviewSummaryResponse anEmptyReviewSummaryResponse() {
    return new ReviewSummaryResponse(
        0.0,
        0L,
        List.of(
            new ReviewSummaryResponse.StarCount(5, 0),
            new ReviewSummaryResponse.StarCount(4, 0),
            new ReviewSummaryResponse.StarCount(3, 0),
            new ReviewSummaryResponse.StarCount(2, 0),
            new ReviewSummaryResponse.StarCount(1, 0)));
  }

  /** 태그 집합 샘플. */
  public static Set<ReviewTag> sampleTags() {
    return Set.of(ReviewTag.FRESH, ReviewTag.KIND);
  }
}
