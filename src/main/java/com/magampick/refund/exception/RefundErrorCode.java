package com.magampick.refund.exception;

import com.magampick.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 환불 도메인 에러 코드. */
@Getter
@RequiredArgsConstructor
public enum RefundErrorCode implements BaseErrorCode {
  REFUND_NOT_FOUND(HttpStatus.NOT_FOUND, "REFUND_NOT_FOUND", "환불 요청을 찾을 수 없습니다"),
  REFUND_NOT_COMPLETED_ORDER(
      HttpStatus.CONFLICT, "REFUND_NOT_COMPLETED_ORDER", "수령완료 주문만 환불 요청이 가능합니다"),
  REFUND_WINDOW_EXPIRED(HttpStatus.CONFLICT, "REFUND_WINDOW_EXPIRED", "환불 요청 기간(3일)이 지났습니다"),
  REFUND_ALREADY_REQUESTED(HttpStatus.CONFLICT, "REFUND_ALREADY_REQUESTED", "이미 환불 요청된 주문입니다"),
  REFUND_ALREADY_PROCESSED(
      HttpStatus.CONFLICT, "REFUND_ALREADY_PROCESSED", "이미 처리(승인/거부)된 환불 요청입니다"),
  REFUND_REASON_REQUIRED(HttpStatus.BAD_REQUEST, "REFUND_REASON_REQUIRED", "환불 사유를 입력해 주세요"),
  REFUND_REJECT_REASON_REQUIRED(
      HttpStatus.BAD_REQUEST, "REFUND_REJECT_REASON_REQUIRED", "거부 사유를 입력해 주세요"),
  REFUND_FORBIDDEN(HttpStatus.FORBIDDEN, "REFUND_FORBIDDEN", "해당 환불 요청에 접근 권한이 없습니다"),
  ;

  private final HttpStatus status;
  private final String code;
  private final String message;
}
