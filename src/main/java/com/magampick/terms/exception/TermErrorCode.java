package com.magampick.terms.exception;

import com.magampick.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 약관 도메인 에러 코드. */
@Getter
@RequiredArgsConstructor
public enum TermErrorCode implements BaseErrorCode {
  REQUIRED_TERMS_NOT_AGREED(
      HttpStatus.BAD_REQUEST, "REQUIRED_TERMS_NOT_AGREED", "필수 약관에 모두 동의해야 합니다"),
  INVALID_TERM(HttpStatus.BAD_REQUEST, "INVALID_TERM", "존재하지 않는 약관입니다"),
  ;

  private final HttpStatus status;
  private final String code;
  private final String message;
}
