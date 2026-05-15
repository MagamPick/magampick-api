package com.magampick.global.security.exception;

import com.magampick.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 토큰/필터/보안 인프라 에러 코드. */
@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements BaseErrorCode {
  TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "TOKEN_EXPIRED", "토큰이 만료되었습니다"),
  INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "유효하지 않은 토큰입니다"),
  ;

  private final HttpStatus status;
  private final String code;
  private final String message;
}
