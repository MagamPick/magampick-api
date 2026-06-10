package com.magampick.geocode.exception;

import com.magampick.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 지오코딩 도메인 에러 코드. */
@Getter
@RequiredArgsConstructor
public enum GeocodeErrorCode implements BaseErrorCode {
  GEOCODING_FAILED(HttpStatus.BAD_REQUEST, "GEOCODING_FAILED", "주소를 좌표로 변환할 수 없습니다"),
  ;

  private final HttpStatus status;
  private final String code;
  private final String message;
}
