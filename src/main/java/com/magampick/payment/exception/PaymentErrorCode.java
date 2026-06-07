package com.magampick.payment.exception;

import com.magampick.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 결제 도메인 에러 코드. */
@Getter
@RequiredArgsConstructor
public enum PaymentErrorCode implements BaseErrorCode {
  PAYMENT_ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "PAYMENT_ORDER_NOT_FOUND", "주문을 찾을 수 없습니다"),
  PAYMENT_ORDER_FORBIDDEN(HttpStatus.FORBIDDEN, "PAYMENT_ORDER_FORBIDDEN", "본인 주문에만 접근할 수 있습니다"),
  PAYMENT_STATUS_MISMATCH(
      HttpStatus.CONFLICT, "PAYMENT_STATUS_MISMATCH", "결제 대기 상태의 주문만 확인할 수 있습니다"),
  PAYMENT_AMOUNT_MISMATCH(
      HttpStatus.BAD_REQUEST, "PAYMENT_AMOUNT_MISMATCH", "결제 금액이 주문 금액과 일치하지 않습니다"),
  PAYMENT_GATEWAY_ERROR(HttpStatus.BAD_GATEWAY, "PAYMENT_GATEWAY_ERROR", "결제 처리 중 오류가 발생했습니다"),
  REFUND_GATEWAY_ERROR(HttpStatus.BAD_GATEWAY, "REFUND_GATEWAY_ERROR", "환불 처리 중 오류가 발생했습니다"),
  ;

  private final HttpStatus status;
  private final String code;
  private final String message;
}
