package com.magampick.product.exception;

import com.magampick.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 일반 상품 도메인 에러 코드. */
@Getter
@RequiredArgsConstructor
public enum ProductErrorCode implements BaseErrorCode {
  PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "상품을 찾을 수 없습니다"),
  PRODUCT_NAME_DUPLICATE(HttpStatus.CONFLICT, "PRODUCT_NAME_DUPLICATE", "이미 같은 이름의 상품이 등록되어 있습니다"),
  PRODUCT_IMAGE_TOO_LARGE(
      HttpStatus.BAD_REQUEST, "PRODUCT_IMAGE_TOO_LARGE", "이미지 파일은 최대 5MB까지 업로드할 수 있습니다"),
  PRODUCT_IMAGE_INVALID_TYPE(
      HttpStatus.BAD_REQUEST, "PRODUCT_IMAGE_INVALID_TYPE", "jpg, png, webp 형식의 이미지만 업로드할 수 있습니다"),
  PRODUCT_IMAGE_UPLOAD_FAILED(
      HttpStatus.INTERNAL_SERVER_ERROR, "PRODUCT_IMAGE_UPLOAD_FAILED", "상품 이미지 업로드에 실패했습니다"),
  ;

  private final HttpStatus status;
  private final String code;
  private final String message;
}
