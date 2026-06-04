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
  EMAIL_ALREADY_REGISTERED(
      HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", "이미 가입된 이메일입니다. 일반 로그인을 이용하세요"),
  PASSWORD_POLICY_VIOLATION(
      HttpStatus.BAD_REQUEST, "PASSWORD_POLICY_VIOLATION", "비밀번호는 8자 이상이며 영문·숫자·특수문자를 모두 포함해야 합니다"),
  PHONE_VERIFICATION_REQUIRED(
      HttpStatus.BAD_REQUEST, "PHONE_VERIFICATION_REQUIRED", "휴대폰 본인인증이 필요합니다"),
  DEFAULT_ADDRESS_REQUIRED(HttpStatus.BAD_REQUEST, "DEFAULT_ADDRESS_REQUIRED", "기본 주소를 입력해야 합니다"),
  SOCIAL_AUTH_FAILED(
      HttpStatus.BAD_GATEWAY, "SOCIAL_AUTH_FAILED", "소셜 로그인 인증에 실패했습니다. 잠시 후 다시 시도해 주세요"),
  KAKAO_EMAIL_REQUIRED(HttpStatus.BAD_REQUEST, "KAKAO_EMAIL_REQUIRED", "카카오 이메일 제공 동의가 필요합니다"),
  ;

  private final HttpStatus status;
  private final String code;
  private final String message;
}
