package com.magampick.payment.service;

/**
 * 결제 PG 추상화. 테스트: {@link StubPaymentGateway} (@Profile("test")), 그 외: {@code TossPaymentGateway}.
 */
public interface PaymentGateway {

  /**
   * 결제 승인 요청.
   *
   * @param command 승인 커맨드 (paymentKey, 멱등키, 금액, 수단)
   * @return 승인 결과 (paymentKey, status, approvedAt)
   * @throws com.magampick.global.exception.BusinessException 승인 실패 시
   */
  PaymentApproval approve(PaymentCommand command);

  /**
   * 결제 취소(환불) 요청.
   *
   * @param command 취소 커맨드 (paymentKey, 취소 사유, 취소 금액)
   * @return 취소 결과 (paymentKey, CANCELED, cancelledAt)
   * @throws com.magampick.global.exception.BusinessException 취소 실패 시
   */
  PaymentCancellation cancel(PaymentCancellationCommand command);
}
