package com.magampick.settlement.exception;

import com.magampick.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 정산 도메인 에러 코드. */
@Getter
@RequiredArgsConstructor
public enum SettlementErrorCode implements BaseErrorCode {
  SETTLEMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "SETTLEMENT_NOT_FOUND", "정산 회차를 찾을 수 없습니다"),
  SETTLEMENT_STORE_FORBIDDEN(
      HttpStatus.FORBIDDEN, "SETTLEMENT_STORE_FORBIDDEN", "본인 매장의 정산만 조회할 수 있습니다"),
  ;

  private final HttpStatus status;
  private final String code;
  private final String message;
}
