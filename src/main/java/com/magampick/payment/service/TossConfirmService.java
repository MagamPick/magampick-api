package com.magampick.payment.service;

import com.magampick.global.exception.BusinessException;
import com.magampick.order.domain.Order;
import com.magampick.order.domain.OrderStatus;
import com.magampick.order.dto.OrderResponse;
import com.magampick.order.mapper.OrderMapper;
import com.magampick.order.repository.OrderRepository;
import com.magampick.payment.domain.Payment;
import com.magampick.payment.domain.PaymentStatus;
import com.magampick.payment.dto.TossConfirmRequest;
import com.magampick.payment.exception.PaymentErrorCode;
import com.magampick.payment.repository.PaymentRepository;
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

  @Transactional
  public OrderResponse confirmPayment(Long customerId, TossConfirmRequest request) {
    Order order =
        orderRepository
            .findById(request.orderId())
            .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_ORDER_NOT_FOUND));

    if (!order.getCustomer().getId().equals(customerId)) {
      throw new BusinessException(PaymentErrorCode.PAYMENT_ORDER_FORBIDDEN);
    }

    if (order.getStatus() != OrderStatus.AWAITING_PAYMENT) {
      throw new BusinessException(PaymentErrorCode.PAYMENT_STATUS_MISMATCH);
    }

    if (order.getTotalPrice().compareTo(request.amount()) != 0) {
      throw new BusinessException(PaymentErrorCode.PAYMENT_AMOUNT_MISMATCH);
    }

    PaymentCommand command =
        new PaymentCommand(
            request.paymentKey(), "order-" + order.getId(), order.getTotalPrice(), "toss");
    PaymentApproval approval = paymentGateway.approve(command);

    if (approval.status() != PaymentStatus.APPROVED) {
      throw new BusinessException(PaymentErrorCode.PAYMENT_GATEWAY_ERROR);
    }

    order.activate();

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

    log.info(
        "토스 결제 확인 완료. orderId={}, customerId={}, paymentKey={}",
        order.getId(),
        customerId,
        approval.paymentKey());

    return orderMapper.toResponse(order);
  }
}
