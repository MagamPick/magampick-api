package com.magampick.point.exception;

import com.magampick.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 포인트 도메인 에러 코드. */
@Getter
@RequiredArgsConstructor
public enum PointErrorCode implements BaseErrorCode {
  INSUFFICIENT_POINTS(HttpStatus.CONFLICT, "INSUFFICIENT_POINTS", "보유 포인트가 부족합니다"),
  ;

  private final HttpStatus status;
  private final String code;
  private final String message;
}
