package com.magampick.payment.service;

import com.magampick.payment.domain.PaymentStatus;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Stub 결제 게이트웨이 — 항상 자동 승인. Phase 5A 전용. 실 토스 연동(Phase 5B~) 시 이 클래스에 @Profile("test") 추가하고,
 * TossPaymentGateway(@Profile("!test")) 추가.
 */
@Slf4j
@Service
public class StubPaymentGateway implements PaymentGateway {

  @Override
  public PaymentApproval approve(PaymentCommand command) {
    // stub: 항상 자동 승인, 가짜 payment_key 발급
    String paymentKey = "stub_" + UUID.randomUUID().toString().replace("-", "");
    LocalDateTime approvedAt = LocalDateTime.now();
    log.info(
        "stub 결제 승인됨. idempotencyKey={}, amount={}, paymentKey={}",
        command.idempotencyKey(),
        command.amount(),
        paymentKey);
    return new PaymentApproval(paymentKey, PaymentStatus.APPROVED, approvedAt);
  }
}
