package com.magampick.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 입력 검증 / 인증 / 권한 / 서버 오류 등 도메인 공통 에러 코드. */
@Getter
@RequiredArgsConstructor
public enum CommonErrorCode implements BaseErrorCode {
  INVALID_INPUT(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "잘못된 입력입니다"),
  UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증이 필요합니다"),
  FORBIDDEN(HttpStatus.FORBIDDEN, "FORBIDDEN", "권한이 없습니다"),
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "서버 오류가 발생했습니다"),
  ;

  private final HttpStatus status;
  private final String code;
  private final String message;
}
