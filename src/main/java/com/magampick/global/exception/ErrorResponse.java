package com.magampick.global.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;

/** 에러 응답 payload. ApiResponse.error 의 error 필드에 담긴다. */
public record ErrorResponse(
    String code,
    String message,
    OffsetDateTime timestamp,
    @JsonInclude(JsonInclude.Include.NON_NULL) List<FieldError> fieldErrors) {

  public static ErrorResponse from(BaseErrorCode errorCode) {
    return new ErrorResponse(
        errorCode.getCode(), errorCode.getMessage(), OffsetDateTime.now(), null);
  }

  public static ErrorResponse from(BaseErrorCode errorCode, List<FieldError> fieldErrors) {
    return new ErrorResponse(
        errorCode.getCode(), errorCode.getMessage(), OffsetDateTime.now(), fieldErrors);
  }

  public record FieldError(String field, String message) {}
}
