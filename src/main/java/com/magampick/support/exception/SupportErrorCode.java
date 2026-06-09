package com.magampick.support.exception;

import com.magampick.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 고객센터(support) 도메인 에러 코드. */
@Getter
@RequiredArgsConstructor
public enum SupportErrorCode implements BaseErrorCode {
  INQUIRY_NOT_FOUND(HttpStatus.NOT_FOUND, "INQUIRY_NOT_FOUND", "문의를 찾을 수 없습니다"),
  INQUIRY_ALREADY_ANSWERED(HttpStatus.CONFLICT, "INQUIRY_ALREADY_ANSWERED", "이미 답변된 문의입니다"),
  ;

  private final HttpStatus status;
  private final String code;
  private final String message;
}
