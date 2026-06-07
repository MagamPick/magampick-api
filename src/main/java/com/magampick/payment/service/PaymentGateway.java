package com.magampick.payment.service;

/**
 * 결제 PG 추상화. Phase 5A: {@link StubPaymentGateway} (자동 승인). 실 토스 샌드박스 연동 시 {@code
 * TossPaymentGateway}(@Profile("!test"))로 교체 — stub 은 @Profile("test") 로 전환.
 */
public interface PaymentGateway {

  /**
   * 결제 승인 요청.
   *
   * @param command 승인 커맨드 (금액, 멱등키, 수단)
   * @return 승인 결과 (paymentKey, status, approvedAt)
   * @throws com.magampick.global.exception.BusinessException PAYMENT_FAILED 시
   */
  PaymentApproval approve(PaymentCommand command);
}
