package com.magampick.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.magampick.global.exception.BusinessException;
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
}
