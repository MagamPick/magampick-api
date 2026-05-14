package com.magampick.global.exception;

import lombok.Getter;

/** 비즈니스 룰 위반 시 던지는 공통 예외. BaseErrorCode 하나로 상태/코드/메시지를 운반한다. */
@Getter
public class BusinessException extends RuntimeException {

  private final transient BaseErrorCode errorCode;

  public BusinessException(BaseErrorCode errorCode) {
    super(errorCode.getMessage());
    this.errorCode = errorCode;
  }
}
