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
  ADDRESS_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "ADDRESS_LIMIT_EXCEEDED", "주소지는 최대 3개까지 등록 가능합니다"),
  DEFAULT_ADDRESS_DELETE_BLOCKED(
      HttpStatus.CONFLICT, "DEFAULT_ADDRESS_DELETE_BLOCKED", "기본 주소지는 삭제할 수 없습니다"),
  LAST_ADDRESS_DELETE_BLOCKED(
      HttpStatus.CONFLICT, "LAST_ADDRESS_DELETE_BLOCKED", "마지막 주소지는 삭제할 수 없습니다"),
  ALIAS_LENGTH(HttpStatus.BAD_REQUEST, "ALIAS_LENGTH", "주소 별칭은 1~20자여야 합니다"),
  GEOCODING_FAILED(HttpStatus.BAD_REQUEST, "GEOCODING_FAILED", "주소 좌표 변환에 실패했습니다"),
  ;

  private final HttpStatus status;
  private final String code;
  private final String message;
}
