package com.magampick.order.exception;

import com.magampick.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 주문 도메인 에러 코드. */
@Getter
@RequiredArgsConstructor
public enum OrderErrorCode implements BaseErrorCode {
  EMPTY_ORDER(HttpStatus.BAD_REQUEST, "EMPTY_ORDER", "주문 항목이 없습니다"),
  MIXED_STORE(HttpStatus.BAD_REQUEST, "MIXED_STORE", "단일 매장의 상품만 주문할 수 있습니다"),
  INVALID_PICKUP_TIME(HttpStatus.BAD_REQUEST, "INVALID_PICKUP_TIME", "유효하지 않은 픽업 시간입니다"),
  PAYMENT_NOT_AGREED(HttpStatus.BAD_REQUEST, "PAYMENT_NOT_AGREED", "결제 동의가 필요합니다"),
  AMOUNT_MISMATCH(HttpStatus.BAD_REQUEST, "AMOUNT_MISMATCH", "요청 금액이 서버 계산 금액과 일치하지 않습니다"),
  PAYMENT_FAILED(HttpStatus.CONFLICT, "PAYMENT_FAILED", "결제 승인에 실패했습니다"),
  ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "주문을 찾을 수 없습니다"),
  ;

  private final HttpStatus status;
  private final String code;
  private final String message;
}
