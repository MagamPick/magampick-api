package com.magampick.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.magampick.coupon.service.CouponService;
import com.magampick.global.exception.BusinessException;
import com.magampick.notification.domain.NotificationCategory;
import com.magampick.notification.service.NotificationService;
import com.magampick.order.domain.Order;
import com.magampick.order.domain.OrderStatus;
import com.magampick.order.domain.PickupType;
import com.magampick.order.dto.OrderResponse;
import com.magampick.order.fixture.OrderFixture;
import com.magampick.order.mapper.OrderMapper;
import com.magampick.order.repository.OrderRepository;
import com.magampick.payment.domain.PaymentStatus;
import com.magampick.payment.dto.TossConfirmRequest;
import com.magampick.payment.exception.PaymentErrorCode;
import com.magampick.payment.repository.PaymentRepository;
import com.magampick.point.service.PointService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TossConfirmServiceTest {

  @Mock OrderRepository orderRepository;
  @Mock PaymentRepository paymentRepository;
  @Mock PaymentGateway paymentGateway;
  @Mock OrderMapper orderMapper;
  @Mock CouponService couponService;
  @Mock PointService pointService;
  @Mock NotificationService notificationService;

  @InjectMocks TossConfirmService tossConfirmService;

  private static final Long CUSTOMER_ID = 1L;
  private static final Long ORDER_ID = 42L;
  private static final BigDecimal AMOUNT = new BigDecimal("6000");
  private static final String PAYMENT_KEY = "toss_paykey_abc";

  private Order awaitingOrder() {
    Order order =
        OrderFixture.anOrderWithStatus(
            OrderFixture.aCustomer(), OrderFixture.aStore(), OrderStatus.AWAITING_PAYMENT);
    ReflectionTestUtils.setField(order, "id", ORDER_ID);
    ReflectionTestUtils.setField(order, "totalPrice", AMOUNT);
    ReflectionTestUtils.setField(order, "pickupType", PickupType.ASAP);
    return order;
  }

  // ── 성공 ─────────────────────────────────────────────────────────────────────

  @Test
  void 토스_결제_확인_성공() {
    // given
    Order order = awaitingOrder();
    given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));
    given(paymentGateway.approve(any()))
        .willReturn(new PaymentApproval(PAYMENT_KEY, PaymentStatus.APPROVED, LocalDateTime.now()));
    given(paymentRepository.save(any())).willReturn(null);
    given(orderMapper.toResponse(any())).willReturn(OrderFixture.anOrderResponse(ORDER_ID));

    TossConfirmRequest req = new TossConfirmRequest(PAYMENT_KEY, ORDER_ID, AMOUNT);

    // when
    OrderResponse resp = tossConfirmService.confirmPayment(CUSTOMER_ID, req);

    // then
    assertThat(resp).isNotNull();
    assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    then(paymentRepository).should().save(any());
    then(paymentGateway).should().approve(any());
  }

  // ── 실패 케이스 ────────────────────────────────────────────────────────────────

  @Test
  void 주문_없음_404() {
    given(orderRepository.findById(ORDER_ID)).willReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                tossConfirmService.confirmPayment(
                    CUSTOMER_ID, new TossConfirmRequest(PAYMENT_KEY, ORDER_ID, AMOUNT)))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(PaymentErrorCode.PAYMENT_ORDER_NOT_FOUND);
  }

  @Test
  void 타인_주문_403() {
    Order order = awaitingOrder();
    given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));

    Long otherCustomerId = 999L;
    assertThatThrownBy(
            () ->
                tossConfirmService.confirmPayment(
                    otherCustomerId, new TossConfirmRequest(PAYMENT_KEY, ORDER_ID, AMOUNT)))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(PaymentErrorCode.PAYMENT_ORDER_FORBIDDEN);
  }

  @Test
  void AWAITING_PAYMENT_아닌_주문_409() {
    Order order =
        OrderFixture.anOrderWithStatus(
            OrderFixture.aCustomer(), OrderFixture.aStore(), OrderStatus.PENDING);
    ReflectionTestUtils.setField(order, "id", ORDER_ID);
    given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));

    assertThatThrownBy(
            () ->
                tossConfirmService.confirmPayment(
                    CUSTOMER_ID, new TossConfirmRequest(PAYMENT_KEY, ORDER_ID, AMOUNT)))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(PaymentErrorCode.PAYMENT_STATUS_MISMATCH);
  }

  @Test
  void 금액_불일치_400() {
    Order order = awaitingOrder();
    given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));

    BigDecimal wrongAmount = new BigDecimal("9999");
    assertThatThrownBy(
            () ->
                tossConfirmService.confirmPayment(
                    CUSTOMER_ID, new TossConfirmRequest(PAYMENT_KEY, ORDER_ID, wrongAmount)))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(PaymentErrorCode.PAYMENT_AMOUNT_MISMATCH);
  }

  // ── 혜택 소비 ─────────────────────────────────────────────────────────────────

  @Test
  void 결제성공_finalAmount기준() {
    // given — totalPrice=6000, finalAmount=4500 (쿠폰1000+포인트500) 주문
    Order order = OrderFixture.anOrderWithBenefits(OrderFixture.aCustomer(), OrderFixture.aStore());
    ReflectionTestUtils.setField(order, "id", ORDER_ID);
    ReflectionTestUtils.setField(order, "status", OrderStatus.AWAITING_PAYMENT);
    given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));
    given(paymentGateway.approve(any()))
        .willReturn(new PaymentApproval(PAYMENT_KEY, PaymentStatus.APPROVED, LocalDateTime.now()));
    given(paymentRepository.save(any())).willReturn(null);
    given(orderMapper.toResponse(any())).willReturn(OrderFixture.anOrderResponse(ORDER_ID));

    BigDecimal finalAmount = new BigDecimal("4500"); // totalPrice=6000 과 다른 값
    TossConfirmRequest req = new TossConfirmRequest(PAYMENT_KEY, ORDER_ID, finalAmount);

    // when
    tossConfirmService.confirmPayment(CUSTOMER_ID, req);

    // then: finalAmount 기준으로 검증 통과 + 혜택 소비 호출됨
    then(couponService).should().use(99L);
    then(pointService).should().use(any(Order.class), eq(500L));
  }

  @Test
  void 결제_확인_완료_시_사장에게_신규주문_알림_발송() {
    // given — store.seller.id=2 (OrderFixture.aStore()), customer.nickname="테스터"
    Order order = awaitingOrder();
    given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));
    given(paymentGateway.approve(any()))
        .willReturn(new PaymentApproval(PAYMENT_KEY, PaymentStatus.APPROVED, LocalDateTime.now()));
    given(paymentRepository.save(any())).willReturn(null);
    given(orderMapper.toResponse(any())).willReturn(OrderFixture.anOrderResponse(ORDER_ID));

    TossConfirmRequest req = new TossConfirmRequest(PAYMENT_KEY, ORDER_ID, AMOUNT);

    // when
    tossConfirmService.confirmPayment(CUSTOMER_ID, req);

    // then — 사장(id=2)에게 newOrder 설정 키로 알림 발송
    then(notificationService)
        .should()
        .notifySeller(
            eq(2L), eq("newOrder"), eq(NotificationCategory.ORDER), any(), any(), eq("/orders"));
  }

  @Test
  void 금액불일치_totalPrice가_아닌_finalAmount기준() {
    // given — totalPrice=6000, finalAmount=4500 인 주문에 totalPrice(6000)를 요청 금액으로 보냄
    // → finalAmount(4500) 기준 검증이면 불일치, totalPrice(6000) 기준이면 통과 (버그 재현)
    Order order = OrderFixture.anOrderWithBenefits(OrderFixture.aCustomer(), OrderFixture.aStore());
    ReflectionTestUtils.setField(order, "id", ORDER_ID);
    ReflectionTestUtils.setField(order, "status", OrderStatus.AWAITING_PAYMENT);
    given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));

    BigDecimal totalPrice = new BigDecimal("6000"); // finalAmount=4500 과 다름
    assertThatThrownBy(
            () ->
                tossConfirmService.confirmPayment(
                    CUSTOMER_ID, new TossConfirmRequest(PAYMENT_KEY, ORDER_ID, totalPrice)))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(PaymentErrorCode.PAYMENT_AMOUNT_MISMATCH);
  }
}
