package com.magampick.analytics.fixture;

import com.magampick.analytics.dto.AnalyticsResponse;
import com.magampick.analytics.dto.AnalyticsResponse.ClearanceMetrics;
import com.magampick.analytics.dto.AnalyticsResponse.OrderMetrics;
import com.magampick.analytics.dto.AnalyticsResponse.ReviewMetrics;
import com.magampick.analytics.dto.AnalyticsResponse.ReviewTagCount;
import com.magampick.analytics.dto.AnalyticsResponse.SalesBar;
import com.magampick.analytics.dto.AnalyticsResponse.SalesMetrics;
import com.magampick.customer.domain.Customer;
import com.magampick.order.domain.Order;
import com.magampick.order.domain.OrderItem;
import com.magampick.order.domain.OrderStatus;
import com.magampick.order.domain.PickupType;
import com.magampick.review.domain.Review;
import com.magampick.review.domain.ReviewReply;
import com.magampick.review.domain.ReviewTag;
import com.magampick.seller.domain.Seller;
import com.magampick.store.domain.Store;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.test.util.ReflectionTestUtils;

/** 통계 도메인 테스트 픽스처. */
public class AnalyticsFixture {

  private AnalyticsFixture() {}

  // ── 도메인 헬퍼 ───────────────────────────────────────────────────────────────

  /** completedAt 이 지정된 COMPLETED 주문 생성. 단위테스트용 — DB 저장 없음. store/customer는 mock 스텁이어도 됨. */
  public static Order aCompletedOrder(
      Customer customer, Store store, BigDecimal totalPrice, LocalDateTime completedAt) {
    Order o =
        Order.builder()
            .customer(customer)
            .store(store)
            .status(OrderStatus.COMPLETED)
            .totalPrice(totalPrice)
            .pickupType(PickupType.ASAP)
            .pickupCode("1234")
            .build();
    ReflectionTestUtils.setField(o, "completedAt", completedAt);
    return o;
  }

  /** createdAt 이 지정된 COMPLETED 주문 (주문 지표 테스트용). */
  public static Order anOrderWithStatus(
      Customer customer, Store store, OrderStatus status, LocalDateTime createdAt) {
    Order o =
        Order.builder()
            .customer(customer)
            .store(store)
            .status(status)
            .totalPrice(new BigDecimal("6000"))
            .pickupType(PickupType.ASAP)
            .pickupCode("1234")
            .build();
    ReflectionTestUtils.setField(o, "createdAt", createdAt);
    return o;
  }

  /** DEAL OrderItem 생성 (단위테스트용). */
  public static OrderItem aDealItem(
      Order order, BigDecimal originalPrice, BigDecimal unitPrice, int quantity) {
    return OrderItem.forDeal(order, null, "크로아상", originalPrice, null, quantity, unitPrice);
  }

  /** MENU OrderItem 생성 (단위테스트용). */
  public static OrderItem aMenuItem(Order order, BigDecimal price, int quantity) {
    return OrderItem.forMenu(order, null, "아메리카노", price, null, quantity, price);
  }

  /** 답글 없는 리뷰. */
  public static Review aReview(Customer customer, Order order, Store store, int rating) {
    Review r =
        Review.builder()
            .customer(customer)
            .order(order)
            .store(store)
            .rating(rating)
            .content("리뷰")
            .build();
    return r;
  }

  /** 답글 있는 리뷰. */
  public static Review aReviewWithReply(
      Customer customer, Order order, Store store, int rating, Seller seller) {
    Review r = aReview(customer, order, store, rating);
    ReviewReply reply = ReviewReply.builder().review(r).seller(seller).content("감사합니다!").build();
    ReflectionTestUtils.setField(r, "reviewReply", reply);
    return r;
  }

  /** 지정 태그 추가된 리뷰. */
  public static void addTags(Review review, ReviewTag... tags) {
    for (ReviewTag tag : tags) {
      review.getTags().add(tag);
    }
  }

  // ── 컨트롤러 테스트용 응답 픽스처 ────────────────────────────────────────────────

  public static AnalyticsResponse aResponse() {
    return new AnalyticsResponse(
        new SalesMetrics(
            50000L,
            10,
            List.of(new SalesBar("14시", 30000L), new SalesBar("16시", 20000L)),
            16667L,
            "14 ~ 15시"),
        new OrderMetrics(5, 3, 1, 1),
        new ClearanceMetrics(10, 10, 15000L, 33),
        new ReviewMetrics(
            4.5,
            3,
            67,
            List.of(
                new ReviewTagCount("맛있어요", 2),
                new ReviewTagCount("신선해요", 1),
                new ReviewTagCount("재구매", 0),
                new ReviewTagCount("픽업 빨라요", 0),
                new ReviewTagCount("양 많아요", 0),
                new ReviewTagCount("가성비", 0),
                new ReviewTagCount("친절해요", 0))));
  }
}
