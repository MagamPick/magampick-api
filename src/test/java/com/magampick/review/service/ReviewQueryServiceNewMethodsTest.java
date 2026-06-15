package com.magampick.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.magampick.customer.domain.Customer;
import com.magampick.order.domain.Order;
import com.magampick.order.domain.OrderStatus;
import com.magampick.order.repository.OrderRepository;
import com.magampick.review.domain.Review;
import com.magampick.review.dto.MyReviewResponse;
import com.magampick.review.dto.ReviewableOrderResponse;
import com.magampick.review.mapper.ReviewMapper;
import com.magampick.review.repository.ReviewRepository;
import com.magampick.seller.domain.Seller;
import com.magampick.store.domain.Store;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReviewQueryServiceNewMethodsTest {

  @Mock ReviewRepository reviewRepository;
  @Mock ReviewMapper reviewMapper;
  @Mock OrderRepository orderRepository;

  @InjectMocks ReviewQueryService reviewQueryService;

  private static final Long CUSTOMER_ID = 1L;
  private static final Long ORDER_ID = 10L;
  private static final Long REVIEW_ID = 20L;

  // ── getReviewableOrders ──────────────────────────────────────────────────────

  @Test
  void 리뷰_가능한_완료_주문_목록_반환() {
    // given
    Customer customer = buildCustomer(CUSTOMER_ID);
    Store store = buildStore();
    Order order = buildCompletedOrder(ORDER_ID, customer, store);
    Review review = buildReview(REVIEW_ID, customer, order, store);

    given(orderRepository.findCompletedOrdersWithDetails(CUSTOMER_ID, OrderStatus.COMPLETED))
        .willReturn(List.of(order));
    given(reviewRepository.findByOrderIdInAndDeletedAtIsNull(List.of(ORDER_ID)))
        .willReturn(List.of(review));

    // when
    List<ReviewableOrderResponse> result = reviewQueryService.getReviewableOrders(CUSTOMER_ID);

    // then
    assertThat(result).hasSize(1);
    assertThat(result.get(0).orderId()).isEqualTo(ORDER_ID);
    assertThat(result.get(0).reviewed()).isTrue();
    assertThat(result.get(0).reviewId()).isEqualTo(REVIEW_ID);
  }

  @Test
  void 리뷰_없는_완료_주문_reviewed_false() {
    // given
    Customer customer = buildCustomer(CUSTOMER_ID);
    Store store = buildStore();
    Order order = buildCompletedOrder(ORDER_ID, customer, store);

    given(orderRepository.findCompletedOrdersWithDetails(CUSTOMER_ID, OrderStatus.COMPLETED))
        .willReturn(List.of(order));
    given(reviewRepository.findByOrderIdInAndDeletedAtIsNull(List.of(ORDER_ID)))
        .willReturn(List.of());

    // when
    List<ReviewableOrderResponse> result = reviewQueryService.getReviewableOrders(CUSTOMER_ID);

    // then
    assertThat(result).hasSize(1);
    assertThat(result.get(0).reviewed()).isFalse();
    assertThat(result.get(0).reviewId()).isNull();
  }

  @Test
  void 완료_주문_없으면_빈_목록() {
    // given
    given(orderRepository.findCompletedOrdersWithDetails(CUSTOMER_ID, OrderStatus.COMPLETED))
        .willReturn(List.of());

    // when
    List<ReviewableOrderResponse> result = reviewQueryService.getReviewableOrders(CUSTOMER_ID);

    // then
    assertThat(result).isEmpty();
  }

  // ── getOrderReview ───────────────────────────────────────────────────────────

  @Test
  void 주문별_리뷰_조회_성공() {
    // given
    Customer customer = buildCustomer(CUSTOMER_ID);
    Store store = buildStore();
    Order order = buildCompletedOrder(ORDER_ID, customer, store);
    Review review = buildReview(REVIEW_ID, customer, order, store);
    MyReviewResponse expected = buildMyReviewResponse(REVIEW_ID);

    given(reviewRepository.findByOrderIdAndDeletedAtIsNull(ORDER_ID))
        .willReturn(Optional.of(review));
    given(reviewMapper.toMyReviewResponse(review)).willReturn(expected);

    // when
    Optional<MyReviewResponse> result = reviewQueryService.getOrderReview(CUSTOMER_ID, ORDER_ID);

    // then
    assertThat(result).isPresent();
    assertThat(result.get().id()).isEqualTo(REVIEW_ID);
  }

  @Test
  void 주문별_리뷰_없으면_empty() {
    // given
    given(reviewRepository.findByOrderIdAndDeletedAtIsNull(ORDER_ID)).willReturn(Optional.empty());

    // when
    Optional<MyReviewResponse> result = reviewQueryService.getOrderReview(CUSTOMER_ID, ORDER_ID);

    // then
    assertThat(result).isEmpty();
  }

  // ── getMyReviews ─────────────────────────────────────────────────────────────

  @Test
  void 소비자_본인_리뷰_목록_최신순_반환() {
    // given
    Customer customer = buildCustomer(CUSTOMER_ID);
    Store store = buildStore();
    Order order = buildCompletedOrder(ORDER_ID, customer, store);
    Review review = buildReview(REVIEW_ID, customer, order, store);
    MyReviewResponse expected = buildMyReviewResponse(REVIEW_ID);

    given(reviewRepository.findByCustomerIdAndDeletedAtIsNullOrderByCreatedAtDesc(CUSTOMER_ID))
        .willReturn(List.of(review));
    given(reviewMapper.toMyReviewResponse(review)).willReturn(expected);

    // when
    List<MyReviewResponse> result = reviewQueryService.getMyReviews(CUSTOMER_ID);

    // then
    assertThat(result).hasSize(1);
    assertThat(result.get(0).id()).isEqualTo(REVIEW_ID);
  }

  @Test
  void 리뷰_없으면_빈_목록() {
    // given
    given(reviewRepository.findByCustomerIdAndDeletedAtIsNullOrderByCreatedAtDesc(CUSTOMER_ID))
        .willReturn(List.of());

    // when
    List<MyReviewResponse> result = reviewQueryService.getMyReviews(CUSTOMER_ID);

    // then
    assertThat(result).isEmpty();
  }

  // ── 헬퍼 ─────────────────────────────────────────────────────────────────────

  private Customer buildCustomer(Long id) {
    Customer c = Customer.builder().email("c@test.com").passwordHash("h").nickname("테스터").build();
    ReflectionTestUtils.setField(c, "id", id);
    return c;
  }

  private Store buildStore() {
    Seller seller = Seller.builder().email("s@test.com").passwordHash("h").ownerName("사장님").build();
    ReflectionTestUtils.setField(seller, "id", 2L);
    Store store =
        Store.builder()
            .seller(seller)
            .businessNumber("1234567890")
            .representativeName("대표자")
            .openDate(LocalDate.of(2024, 1, 1))
            .name("테스트 매장")
            .roadAddress("서울 강남구")
            .zonecode("06158")
            .location(null)
            .phone("0212345678")
            .build();
    ReflectionTestUtils.setField(store, "id", 100L);
    return store;
  }

  private Order buildCompletedOrder(Long id, Customer customer, Store store) {
    Order order =
        Order.builder()
            .customer(customer)
            .store(store)
            .status(OrderStatus.COMPLETED)
            .totalPrice(BigDecimal.valueOf(10000))
            .build();
    ReflectionTestUtils.setField(order, "id", id);
    ReflectionTestUtils.setField(order, "completedAt", LocalDateTime.now());
    return order;
  }

  private Review buildReview(Long id, Customer customer, Order order, Store store) {
    Review review =
        Review.builder()
            .customer(customer)
            .order(order)
            .store(store)
            .rating(4)
            .content("맛있어요")
            .build();
    ReflectionTestUtils.setField(review, "id", id);
    return review;
  }

  private MyReviewResponse buildMyReviewResponse(Long reviewId) {
    return new MyReviewResponse(
        reviewId,
        100L,
        "테스트 매장",
        List.of(),
        4,
        "맛있어요",
        List.of(),
        List.of(),
        OffsetDateTime.now(ZoneOffset.ofHours(9)),
        null);
  }
}
