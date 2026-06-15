package com.magampick.payment.service;

import com.magampick.payment.domain.PaymentStatus;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** Stub 결제 게이트웨이 — 테스트 전용 (@Profile("test")). approve 는 항상 자동 승인, cancel 은 항상 성공. */
@Slf4j
@Service
@Profile("test")
public class StubPaymentGateway implements PaymentGateway {

  @Override
  public PaymentApproval approve(PaymentCommand command) {
    String paymentKey = "stub_" + UUID.randomUUID().toString().replace("-", "");
    LocalDateTime approvedAt = LocalDateTime.now();
    log.info(
        "stub 결제 승인됨. idempotencyKey={}, amount={}, paymentKey={}",
        command.idempotencyKey(),
        command.amount(),
        paymentKey);
    return new PaymentApproval(paymentKey, PaymentStatus.APPROVED, approvedAt);
  }

  @Override
  public PaymentCancellation cancel(PaymentCancellationCommand command) {
    log.info(
        "stub 결제 취소됨. paymentKey={}, reason={}, amount={}",
        command.paymentKey(),
        command.cancelReason(),
        command.cancelAmount());
    return new PaymentCancellation(
        command.paymentKey(), PaymentStatus.CANCELED, LocalDateTime.now());
  }
}
