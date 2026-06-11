package com.magampick.payment.service;

import com.magampick.coupon.service.CouponService;
import com.magampick.global.exception.BusinessException;
import com.magampick.notification.domain.NotificationCategory;
import com.magampick.notification.service.NotificationService;
import com.magampick.order.domain.Order;
import com.magampick.order.dto.OrderResponse;
import com.magampick.order.mapper.OrderMapper;
import com.magampick.order.repository.OrderRepository;
import com.magampick.payment.domain.Payment;
import com.magampick.payment.domain.PaymentStatus;
import com.magampick.payment.dto.TossConfirmRequest;
import com.magampick.payment.exception.PaymentErrorCode;
import com.magampick.payment.repository.PaymentRepository;
import com.magampick.point.service.PointService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 토스 결제 확인 서비스. AWAITING_PAYMENT → 토스 confirm API → PENDING 활성화 + Payment 저장. */
@Slf4j
@Service
@RequiredArgsConstructor
public class TossConfirmService {

  private final OrderRepository orderRepository;
  private final PaymentRepository paymentRepository;
  private final PaymentGateway paymentGateway;
  private final OrderMapper orderMapper;
  private final CouponService couponService;
  private final PointService pointService;
  private final NotificationService notificationService;

  @Transactional
  public OrderResponse confirmPayment(Long customerId, TossConfirmRequest request) {
    // 주문 조회
    Order order =
        orderRepository
            .findById(request.orderId())
            .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_ORDER_NOT_FOUND));

    // 소유권 확인
    if (!order.isOwnedBy(customerId)) {
      throw new BusinessException(PaymentErrorCode.PAYMENT_ORDER_FORBIDDEN);
    }

    // 결제 상태 확인
    if (!order.isAwaitingPayment()) {
      throw new BusinessException(PaymentErrorCode.PAYMENT_STATUS_MISMATCH);
    }

    // 결제 금액 검증
    if (order.getFinalAmount().compareTo(request.amount()) != 0) {
      throw new BusinessException(PaymentErrorCode.PAYMENT_AMOUNT_MISMATCH);
    }

    // PG 승인 요청
    PaymentCommand command =
        new PaymentCommand(
            request.paymentKey(), "order-" + order.getId(), order.getFinalAmount(), "toss");
    PaymentApproval approval = paymentGateway.approve(command);

    // PG 응답 확인
    if (approval.status() != PaymentStatus.APPROVED) {
      throw new BusinessException(PaymentErrorCode.PAYMENT_GATEWAY_ERROR);
    }

    // 혜택 차감 및 주문 활성화
    if (order.hasCoupon()) couponService.use(order.getUserCouponId());
    if (order.hasUsedPoints()) pointService.use(order, order.getPointUsed());
    order.activate();
    // markUsed(@Modifying clearAutomatically=true) 가 EntityManager 를 비워 order 가 detached 되므로
    // 명시적 save 로 PENDING 상태를 DB 에 반영한다.
    orderRepository.save(order);

    // 결제 정보 저장
    Payment payment =
        Payment.builder()
            .order(order)
            .provider("TOSS")
            .method("toss")
            .paymentKey(approval.paymentKey())
            .amount(request.amount())
            .status(approval.status())
            .approvedAt(approval.approvedAt())
            .build();
    paymentRepository.save(payment);

    // 알림 발송
    notificationService.notifySeller(
        order.getStore().getSeller().getId(),
        "newOrder",
        NotificationCategory.ORDER,
        "새 주문이 들어왔어요",
        order.getCustomer().getNickname() + "님이 주문했어요.",
        "/orders");

    log.info(
        "토스 결제 확인 완료. orderId={}, customerId={}, paymentKey={}",
        order.getId(),
        customerId,
        approval.paymentKey());

    return orderMapper.toResponse(order);
  }
}
