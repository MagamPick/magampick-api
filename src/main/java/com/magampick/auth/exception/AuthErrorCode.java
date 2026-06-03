package com.magampick.auth.exception;

import com.magampick.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 인증/회원 관련 도메인 에러 코드. */
@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements BaseErrorCode {
  INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "이메일 또는 비밀번호가 올바르지 않습니다"),
  EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "이미 사용 중인 이메일입니다"),
  PASSWORD_POLICY_VIOLATION(
      HttpStatus.BAD_REQUEST, "PASSWORD_POLICY_VIOLATION", "비밀번호는 8자 이상이며 영문·숫자·특수문자를 모두 포함해야 합니다"),
  PHONE_VERIFICATION_REQUIRED(
      HttpStatus.BAD_REQUEST, "PHONE_VERIFICATION_REQUIRED", "휴대폰 본인인증이 필요합니다"),
  DEFAULT_ADDRESS_REQUIRED(HttpStatus.BAD_REQUEST, "DEFAULT_ADDRESS_REQUIRED", "기본 주소를 입력해야 합니다"),
  ;

  private final HttpStatus status;
  private final String code;
  private final String message;
}
