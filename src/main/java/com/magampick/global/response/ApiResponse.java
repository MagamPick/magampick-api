package com.magampick.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.magampick.global.exception.ErrorResponse;

/** 모든 API 응답을 감싸는 통일 envelope. 성공이면 data, 실패면 error 만 채워진다. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, T data, ErrorResponse error) {

  public static <T> ApiResponse<T> success(T data) {
    return new ApiResponse<>(true, data, null);
  }

  public static <T> ApiResponse<T> error(ErrorResponse error) {
    return new ApiResponse<>(false, null, error);
  }
}
