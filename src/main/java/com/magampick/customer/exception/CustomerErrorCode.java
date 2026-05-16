package com.magampick.customer.exception;

import com.magampick.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 소비자 도메인 에러 코드. */
@Getter
@RequiredArgsConstructor
public enum CustomerErrorCode implements BaseErrorCode {
  CUSTOMER_NOT_FOUND(HttpStatus.NOT_FOUND, "CUSTOMER_NOT_FOUND", "소비자 계정을 찾을 수 없습니다"),
  ;

  private final HttpStatus status;
  private final String code;
  private final String message;
}
