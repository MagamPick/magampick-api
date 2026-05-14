package com.magampick.global.security.exception;

import com.magampick.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 인증 관련 에러 코드. 현재는 토큰 검증 코드만 정의 — 로그인/가입 코드(INVALID_CREDENTIALS 등)는 회원·인증 도메인 이슈에서 추가한다 (auth.md
 * §13).
 */
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
