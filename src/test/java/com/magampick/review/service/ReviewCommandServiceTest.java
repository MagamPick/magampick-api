package com.magampick.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.magampick.customer.domain.Customer;
import com.magampick.customer.repository.CustomerRepository;
import com.magampick.global.exception.BusinessException;
import com.magampick.notification.domain.NotificationCategory;
import com.magampick.notification.service.NotificationService;
import com.magampick.order.domain.Order;
import com.magampick.order.domain.OrderStatus;
import com.magampick.order.repository.OrderRepository;
import com.magampick.review.domain.Review;
import com.magampick.review.domain.ReviewTag;
import com.magampick.review.dto.CreateReviewRequest;
import com.magampick.review.dto.MyReviewResponse;
import com.magampick.review.dto.ReviewReplyRequest;
import com.magampick.review.dto.StoreReviewResponse;
import com.magampick.review.dto.UpdateReviewRequest;
import com.magampick.review.exception.ReviewErrorCode;
import com.magampick.review.mapper.ReviewMapper;
import com.magampick.review.repository.ReviewReplyRepository;
import com.magampick.review.repository.ReviewRepository;
import com.magampick.seller.domain.Seller;
import com.magampick.seller.repository.SellerRepository;
import com.magampick.store.domain.Store;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReviewCommandServiceTest {

  @Mock ReviewRepository reviewRepository;
  @Mock ReviewReplyRepository reviewReplyRepository;
  @Mock OrderRepository orderRepository;
  @Mock CustomerRepository customerRepository;
  @Mock SellerRepository sellerRepository;
  @Mock ReviewMapper reviewMapper;
  @Mock NotificationService notificationService;

  @InjectMocks ReviewCommandService reviewCommandService;

  private static final Long CUSTOMER_ID = 1L;
  private static final Long SELLER_ID = 2L;
  private static final Long ORDER_ID = 10L;
  private static final Long REVIEW_ID = 20L;

  // ── createReview ────────────────────────────────────────────────────────────

  @Test
  void 리뷰_작성_성공() {
    // given
    Customer customer = buildCustomer(CUSTOMER_ID);
    Store store = buildStore(SELLER_ID);
    Order order = buildOrder(ORDER_ID, customer, store, OrderStatus.COMPLETED);
    CreateReviewRequest req =
        new CreateReviewRequest(4, "맛있어요", Set.of(ReviewTag.FRESH), List.of("/img/1.jpg"));
    Review review = buildReview(REVIEW_ID, customer, order, store);
    MyReviewResponse expected = buildMyReviewResponse(REVIEW_ID);

    given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));
    given(reviewRepository.findByOrderId(ORDER_ID)).willReturn(Optional.empty());
    given(customerRepository.findById(CUSTOMER_ID)).willReturn(Optional.of(customer));
    given(reviewRepository.save(any())).willReturn(review);
    given(reviewMapper.toMyReviewResponse(review)).willReturn(expected);

    // when
    MyReviewResponse result = reviewCommandService.createReview(CUSTOMER_ID, ORDER_ID, req);

    // then
    assertThat(result.id()).isEqualTo(REVIEW_ID);
    then(reviewRepository).should().save(any(Review.class));
  }

  @Test
  void 리뷰_작성_실패_COMPLETED_아님() {
    // given
    Customer customer = buildCustomer(CUSTOMER_ID);
    Store store = buildStore(SELLER_ID);
    Order order = buildOrder(ORDER_ID, customer, store, OrderStatus.PREPARING);

    given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));

    // when / then
    assertThatThrownBy(() -> reviewCommandService.createReview(CUSTOMER_ID, ORDER_ID, null))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex ->
                assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ReviewErrorCode.REVIEW_NOT_ELIGIBLE));
  }

  @Test
  void 리뷰_작성_실패_이미_존재() {
    // given
    Customer customer = buildCustomer(CUSTOMER_ID);
    Store store = buildStore(SELLER_ID);
    Order order = buildOrder(ORDER_ID, customer, store, OrderStatus.COMPLETED);
    Review existing = buildReview(REVIEW_ID, customer, order, store);

    given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));
    given(reviewRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(existing));

    // when / then
    assertThatThrownBy(() -> reviewCommandService.createReview(CUSTOMER_ID, ORDER_ID, null))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex ->
                assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ReviewErrorCode.REVIEW_ALREADY_EXISTS));
  }

  @Test
  void 리뷰_작성_실패_주문없음() {
    // given
    given(orderRepository.findById(ORDER_ID)).willReturn(Optional.empty());

    // when / then
    assertThatThrownBy(() -> reviewCommandService.createReview(CUSTOMER_ID, ORDER_ID, null))
        .isInstanceOf(BusinessException.class);
  }

  // ── updateReview ────────────────────────────────────────────────────────────

  @Test
  void 리뷰_수정_성공() {
    // given
    Customer customer = buildCustomer(CUSTOMER_ID);
    Store store = buildStore(SELLER_ID);
    Order order = buildOrder(ORDER_ID, customer, store, OrderStatus.COMPLETED);
    Review review = buildReview(REVIEW_ID, customer, order, store);
    UpdateReviewRequest req =
        new UpdateReviewRequest(5, "더 맛있어요", Set.of(ReviewTag.KIND), List.of());
    MyReviewResponse expected = buildMyReviewResponse(REVIEW_ID);

    given(reviewRepository.findById(REVIEW_ID)).willReturn(Optional.of(review));
    given(reviewMapper.toMyReviewResponse(review)).willReturn(expected);

    // when
    MyReviewResponse result = reviewCommandService.updateReview(CUSTOMER_ID, REVIEW_ID, req);

    // then
    assertThat(result.id()).isEqualTo(REVIEW_ID);
  }

  @Test
  void 리뷰_수정_실패_본인아님() {
    // given
    Customer customer = buildCustomer(CUSTOMER_ID);
    Store store = buildStore(SELLER_ID);
    Order order = buildOrder(ORDER_ID, customer, store, OrderStatus.COMPLETED);
    Review review = buildReview(REVIEW_ID, customer, order, store);

    given(reviewRepository.findById(REVIEW_ID)).willReturn(Optional.of(review));

    // when / then
    Long otherId = 99L;
    assertThatThrownBy(() -> reviewCommandService.updateReview(otherId, REVIEW_ID, null))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex ->
                assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ReviewErrorCode.REVIEW_FORBIDDEN));
  }

  @Test
  void 리뷰_수정_실패_답글_있음() {
    // given
    Customer customer = buildCustomer(CUSTOMER_ID);
    Store store = buildStore(SELLER_ID);
    Order order = buildOrder(ORDER_ID, customer, store, OrderStatus.COMPLETED);
    Review review = buildReviewWithReply(REVIEW_ID, customer, order, store);

    given(reviewRepository.findById(REVIEW_ID)).willReturn(Optional.of(review));

    // when / then
    assertThatThrownBy(() -> reviewCommandService.updateReview(CUSTOMER_ID, REVIEW_ID, null))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex ->
                assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ReviewErrorCode.REVIEW_LOCKED));
  }

  // ── deleteReview ────────────────────────────────────────────────────────────

  @Test
  void 리뷰_삭제_성공() {
    // given
    Customer customer = buildCustomer(CUSTOMER_ID);
    Store store = buildStore(SELLER_ID);
    Order order = buildOrder(ORDER_ID, customer, store, OrderStatus.COMPLETED);
    Review review = buildReview(REVIEW_ID, customer, order, store);

    given(reviewRepository.findById(REVIEW_ID)).willReturn(Optional.of(review));

    // when
    reviewCommandService.deleteReview(CUSTOMER_ID, REVIEW_ID);

    // then — delete()가 호출되어 deletedAt 설정
    assertThat(review.isDeleted()).isTrue();
  }

  @Test
  void 리뷰_삭제_실패_본인아님() {
    // given
    Customer customer = buildCustomer(CUSTOMER_ID);
    Store store = buildStore(SELLER_ID);
    Order order = buildOrder(ORDER_ID, customer, store, OrderStatus.COMPLETED);
    Review review = buildReview(REVIEW_ID, customer, order, store);

    given(reviewRepository.findById(REVIEW_ID)).willReturn(Optional.of(review));

    // when / then
    assertThatThrownBy(() -> reviewCommandService.deleteReview(99L, REVIEW_ID))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex ->
                assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ReviewErrorCode.REVIEW_FORBIDDEN));
  }

  @Test
  void 리뷰_삭제_실패_답글_있음() {
    // given
    Customer customer = buildCustomer(CUSTOMER_ID);
    Store store = buildStore(SELLER_ID);
    Order order = buildOrder(ORDER_ID, customer, store, OrderStatus.COMPLETED);
    Review review = buildReviewWithReply(REVIEW_ID, customer, order, store);

    given(reviewRepository.findById(REVIEW_ID)).willReturn(Optional.of(review));

    // when / then
    assertThatThrownBy(() -> reviewCommandService.deleteReview(CUSTOMER_ID, REVIEW_ID))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex ->
                assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ReviewErrorCode.REVIEW_LOCKED));
  }

  // ── replyToReview ───────────────────────────────────────────────────────────

  @Test
  void 답글_작성_성공() {
    // given
    Seller seller = buildSeller(SELLER_ID);
    Customer customer = buildCustomer(CUSTOMER_ID);
    Store store = buildStore(SELLER_ID);
    Order order = buildOrder(ORDER_ID, customer, store, OrderStatus.COMPLETED);
    Review review = buildReview(REVIEW_ID, customer, order, store);
    ReviewReplyRequest req = new ReviewReplyRequest("감사합니다!");
    StoreReviewResponse expected = buildStoreReviewResponse(REVIEW_ID);

    given(sellerRepository.findById(SELLER_ID)).willReturn(Optional.of(seller));
    given(reviewRepository.findById(REVIEW_ID)).willReturn(Optional.of(review));
    given(reviewReplyRepository.existsByReviewId(REVIEW_ID)).willReturn(false);
    given(reviewReplyRepository.save(any())).willReturn(null);
    given(reviewMapper.toResponse(review)).willReturn(expected);

    // when
    StoreReviewResponse result = reviewCommandService.replyToReview(SELLER_ID, REVIEW_ID, req);

    // then
    assertThat(result.id()).isEqualTo(REVIEW_ID);
    then(reviewReplyRepository).should().save(any());
  }

  @Test
  void 답글_작성_실패_본인매장아님() {
    // given
    Long otherSellerId = 99L;
    Seller seller = buildSeller(otherSellerId);
    Customer customer = buildCustomer(CUSTOMER_ID);
    Store store = buildStore(SELLER_ID); // store의 seller.id = SELLER_ID
    Order order = buildOrder(ORDER_ID, customer, store, OrderStatus.COMPLETED);
    Review review = buildReview(REVIEW_ID, customer, order, store);

    given(sellerRepository.findById(otherSellerId)).willReturn(Optional.of(seller));
    given(reviewRepository.findById(REVIEW_ID)).willReturn(Optional.of(review));

    // when / then
    assertThatThrownBy(() -> reviewCommandService.replyToReview(otherSellerId, REVIEW_ID, null))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex ->
                assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ReviewErrorCode.REPLY_STORE_FORBIDDEN));
  }

  @Test
  void 답글_작성_실패_이미_존재() {
    // given
    Seller seller = buildSeller(SELLER_ID);
    Customer customer = buildCustomer(CUSTOMER_ID);
    Store store = buildStore(SELLER_ID);
    Order order = buildOrder(ORDER_ID, customer, store, OrderStatus.COMPLETED);
    Review review = buildReview(REVIEW_ID, customer, order, store);
    ReviewReplyRequest req = new ReviewReplyRequest("감사합니다!");

    given(sellerRepository.findById(SELLER_ID)).willReturn(Optional.of(seller));
    given(reviewRepository.findById(REVIEW_ID)).willReturn(Optional.of(review));
    given(reviewReplyRepository.existsByReviewId(REVIEW_ID)).willReturn(true);

    // when / then
    assertThatThrownBy(() -> reviewCommandService.replyToReview(SELLER_ID, REVIEW_ID, req))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex ->
                assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ReviewErrorCode.REPLY_ALREADY_EXISTS));
  }

  // ── 헬퍼 ─────────────────────────────────────────────────────────────────────

  private Customer buildCustomer(Long id) {
    Customer c = Customer.builder().email("c@test.com").passwordHash("h").nickname("테스터").build();
    ReflectionTestUtils.setField(c, "id", id);
    return c;
  }

  private Seller buildSeller(Long id) {
    Seller s = Seller.builder().email("s@test.com").passwordHash("h").ownerName("사장님").build();
    ReflectionTestUtils.setField(s, "id", id);
    return s;
  }

  private Store buildStore(Long sellerId) {
    Seller seller = buildSeller(sellerId);
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

  private Order buildOrder(Long id, Customer customer, Store store, OrderStatus status) {
    Order order = Order.builder().customer(customer).store(store).status(status).build();
    ReflectionTestUtils.setField(order, "id", id);
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

  private Review buildReviewWithReply(Long id, Customer customer, Order order, Store store) {
    Review review = buildReview(id, customer, order, store);
    com.magampick.review.domain.ReviewReply reply =
        com.magampick.review.domain.ReviewReply.builder()
            .review(review)
            .seller(buildSeller(SELLER_ID))
            .content("감사합니다!")
            .build();
    ReflectionTestUtils.setField(review, "reviewReply", reply);
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

  private StoreReviewResponse buildStoreReviewResponse(Long reviewId) {
    return new StoreReviewResponse(
        reviewId,
        "테스터",
        4,
        "맛있어요",
        OffsetDateTime.now(ZoneOffset.ofHours(9)),
        List.of(),
        List.of(),
        List.of(),
        null);
  }

  // ── 알림 발송 ────────────────────────────────────────────────────────────────

  @Test
  void 리뷰_작성_시_사장에게_알림_발송() {
    // given — store.seller.id=SELLER_ID(2L), customer.nickname="테스터"
    Customer customer = buildCustomer(CUSTOMER_ID);
    Store store = buildStore(SELLER_ID);
    Order order = buildOrder(ORDER_ID, customer, store, OrderStatus.COMPLETED);
    CreateReviewRequest req =
        new CreateReviewRequest(5, "맛있어요", Set.of(ReviewTag.FRESH), List.of());
    Review review = buildReview(REVIEW_ID, customer, order, store);
    MyReviewResponse expected = buildMyReviewResponse(REVIEW_ID);

    given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));
    given(reviewRepository.findByOrderId(ORDER_ID)).willReturn(Optional.empty());
    given(customerRepository.findById(CUSTOMER_ID)).willReturn(Optional.of(customer));
    given(reviewRepository.save(any())).willReturn(review);
    given(reviewMapper.toMyReviewResponse(review)).willReturn(expected);

    // when
    reviewCommandService.createReview(CUSTOMER_ID, ORDER_ID, req);

    // then — 사장(id=SELLER_ID)에게 newReview 설정 키로 알림 발송
    then(notificationService)
        .should()
        .notifySeller(
            eq(SELLER_ID),
            eq("newReview"),
            eq(NotificationCategory.REVIEW),
            any(),
            any(),
            eq("/reviews"));
  }

  @Test
  void 답글_작성_시_소비자에게_알림_발송() {
    // given — review.customer.id=CUSTOMER_ID(1L)
    Seller seller = buildSeller(SELLER_ID);
    Customer customer = buildCustomer(CUSTOMER_ID);
    Store store = buildStore(SELLER_ID);
    Order order = buildOrder(ORDER_ID, customer, store, OrderStatus.COMPLETED);
    Review review = buildReview(REVIEW_ID, customer, order, store);
    ReviewReplyRequest req = new ReviewReplyRequest("감사합니다!");
    StoreReviewResponse expected = buildStoreReviewResponse(REVIEW_ID);

    given(sellerRepository.findById(SELLER_ID)).willReturn(Optional.of(seller));
    given(reviewRepository.findById(REVIEW_ID)).willReturn(Optional.of(review));
    given(reviewReplyRepository.existsByReviewId(REVIEW_ID)).willReturn(false);
    given(reviewReplyRepository.save(any())).willReturn(null);
    given(reviewMapper.toResponse(review)).willReturn(expected);

    // when
    reviewCommandService.replyToReview(SELLER_ID, REVIEW_ID, req);

    // then — 소비자(id=CUSTOMER_ID)에게 reviewReply 설정 키로 알림 발송
    then(notificationService)
        .should()
        .notifyCustomer(
            eq(CUSTOMER_ID),
            eq("reviewReply"),
            eq(NotificationCategory.REVIEW),
            any(),
            any(),
            eq("/reviews/my"));
  }
}
