package com.magampick.phone.exception;

import com.magampick.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 본인인증(SMS OTP) 도메인 에러 코드. 노션 본인인증 명세 AC 기준. */
@Getter
@RequiredArgsConstructor
public enum PhoneVerificationErrorCode implements BaseErrorCode {
  PHONE_FORMAT_INVALID(HttpStatus.BAD_REQUEST, "PHONE_FORMAT_INVALID", "휴대폰 번호 형식이 올바르지 않습니다"),
  OTP_RESEND_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "OTP_RESEND_LIMIT", "잠시 후 다시 시도해 주세요"),
  OTP_DAILY_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "OTP_DAILY_LIMIT", "일일 인증번호 발송 한도를 초과했습니다"),
  OTP_EXPIRED(HttpStatus.BAD_REQUEST, "OTP_EXPIRED", "인증번호가 만료되었습니다. 재발송해 주세요"),
  OTP_INVALID(HttpStatus.BAD_REQUEST, "OTP_INVALID", "인증번호가 일치하지 않습니다"),
  OTP_ATTEMPT_LIMIT(
      HttpStatus.TOO_MANY_REQUESTS, "OTP_ATTEMPT_LIMIT", "인증 시도 횟수를 초과했습니다. 재발송해 주세요"),
  PHONE_VERIFICATION_EXPIRED(
      HttpStatus.BAD_REQUEST, "PHONE_VERIFICATION_EXPIRED", "본인인증이 만료되었습니다. 다시 인증해 주세요"),
  SMS_SEND_FAILED(HttpStatus.BAD_GATEWAY, "SMS_SEND_FAILED", "인증번호 발송에 실패했습니다. 잠시 후 다시 시도해 주세요"),
  ;

  private final HttpStatus status;
  private final String code;
  private final String message;
}
