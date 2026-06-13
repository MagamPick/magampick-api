package com.magampick.coupon.exception;

import com.magampick.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 쿠폰 도메인 에러 코드. */
@Getter
@RequiredArgsConstructor
public enum CouponErrorCode implements BaseErrorCode {
  COUPON_NOT_FOUND(HttpStatus.NOT_FOUND, "COUPON_NOT_FOUND", "쿠폰을 찾을 수 없습니다"),
  COUPON_NOT_AVAILABLE(HttpStatus.CONFLICT, "COUPON_NOT_AVAILABLE", "발급할 수 없는 쿠폰입니다"),
  COUPON_ALREADY_CLAIMED(HttpStatus.CONFLICT, "COUPON_ALREADY_CLAIMED", "이미 받은 쿠폰입니다"),
  COUPON_SOLD_OUT(HttpStatus.CONFLICT, "COUPON_SOLD_OUT", "마감된 쿠폰입니다"),
  INVALID_DISCOUNT_RATE(HttpStatus.BAD_REQUEST, "INVALID_DISCOUNT_RATE", "할인율은 1~100 사이여야 합니다"),
  INVALID_EVENT_PERIOD(
      HttpStatus.BAD_REQUEST, "INVALID_EVENT_PERIOD", "이벤트 노출 종료일이 시작일보다 앞설 수 없습니다"),
  INVALID_COUPON_VALIDITY(
      HttpStatus.BAD_REQUEST, "INVALID_COUPON_VALIDITY", "쿠폰 만료일은 노출 종료일 이후여야 합니다"),
  ;

  private final HttpStatus status;
  private final String code;
  private final String message;
}
