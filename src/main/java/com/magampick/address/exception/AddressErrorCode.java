package com.magampick.address.exception;

import com.magampick.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 주소지 도메인 에러 코드. */
@Getter
@RequiredArgsConstructor
public enum AddressErrorCode implements BaseErrorCode {
  ADDRESS_NOT_FOUND(HttpStatus.NOT_FOUND, "ADDRESS_NOT_FOUND", "주소지를 찾을 수 없습니다"),
  ADDRESS_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "ADDRESS_LIMIT_EXCEEDED", "주소지는 최대 3개까지 등록 가능합니다"),
  ;

  private final HttpStatus status;
  private final String code;
  private final String message;
}
