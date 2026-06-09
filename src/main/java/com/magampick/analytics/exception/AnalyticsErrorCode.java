package com.magampick.analytics.exception;

import com.magampick.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 통계 도메인 에러 코드. */
@Getter
@RequiredArgsConstructor
public enum AnalyticsErrorCode implements BaseErrorCode {
  INVALID_PERIOD(HttpStatus.BAD_REQUEST, "INVALID_PERIOD", "유효하지 않은 기간입니다"),
  ANALYTICS_STORE_FORBIDDEN(
      HttpStatus.FORBIDDEN, "ANALYTICS_STORE_FORBIDDEN", "본인 매장의 통계만 조회할 수 있습니다"),
  ;

  private final HttpStatus status;
  private final String code;
  private final String message;
}
