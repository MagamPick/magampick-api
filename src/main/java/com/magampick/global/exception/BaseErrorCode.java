package com.magampick.global.exception;

import org.springframework.http.HttpStatus;

/** 도메인별 ErrorCode enum 이 구현하는 공통 인터페이스. */
public interface BaseErrorCode {

  HttpStatus getStatus();

  String getCode();

  String getMessage();
}
