package com.magampick.store.exception;

import com.magampick.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 매장 도메인 에러 코드. */
@Getter
@RequiredArgsConstructor
public enum StoreErrorCode implements BaseErrorCode {
  STORE_NOT_FOUND(HttpStatus.NOT_FOUND, "STORE_NOT_FOUND", "매장을 찾을 수 없습니다"),
  STORE_CATEGORY_NOT_FOUND(
      HttpStatus.NOT_FOUND, "STORE_CATEGORY_NOT_FOUND", "존재하지 않는 카테고리가 포함되어 있습니다"),
  STORE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "STORE_ACCESS_DENIED", "해당 매장에 대한 접근 권한이 없습니다"),
  STORE_ALREADY_REVIEWED(HttpStatus.CONFLICT, "STORE_ALREADY_REVIEWED", "이미 심사가 완료된 매장입니다"),
  STORE_IMAGE_TOO_LARGE(
      HttpStatus.BAD_REQUEST, "STORE_IMAGE_TOO_LARGE", "이미지 파일은 최대 5MB까지 업로드할 수 있습니다"),
  STORE_IMAGE_INVALID_TYPE(
      HttpStatus.BAD_REQUEST, "STORE_IMAGE_INVALID_TYPE", "jpg, png, webp 형식의 이미지만 업로드할 수 있습니다"),
  STORE_IMAGE_UPLOAD_FAILED(
      HttpStatus.INTERNAL_SERVER_ERROR, "STORE_IMAGE_UPLOAD_FAILED", "매장 이미지 업로드에 실패했습니다"),
  ;

  private final HttpStatus status;
  private final String code;
  private final String message;
}
