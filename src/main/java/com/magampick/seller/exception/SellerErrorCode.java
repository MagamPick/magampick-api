package com.magampick.seller.exception;

import com.magampick.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 사장 도메인 에러 코드. */
@Getter
@RequiredArgsConstructor
public enum SellerErrorCode implements BaseErrorCode {
  SELLER_NOT_FOUND(HttpStatus.NOT_FOUND, "SELLER_NOT_FOUND", "사장 계정을 찾을 수 없습니다"),
  ;

  private final HttpStatus status;
  private final String code;
  private final String message;
}
