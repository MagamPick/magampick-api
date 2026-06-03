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
  STORE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "STORE_ACCESS_DENIED", "해당 매장에 대한 접근 권한이 없습니다"),
  BUSINESS_NUMBER_FORMAT_INVALID(
      HttpStatus.BAD_REQUEST, "BUSINESS_NUMBER_FORMAT_INVALID", "사업자 번호는 숫자 10자리여야 합니다"),
  BUSINESS_NUMBER_NOT_ACTIVE(
      HttpStatus.BAD_REQUEST, "BUSINESS_NUMBER_NOT_ACTIVE", "정상 영업 중인 사업자 번호가 아닙니다"),
  BUSINESS_INFO_MISMATCH(
      HttpStatus.BAD_REQUEST, "BUSINESS_INFO_MISMATCH", "사업자 번호·대표자명·개업일자가 일치하지 않습니다"),
  BUSINESS_NUMBER_VERIFICATION_FAILED(
      HttpStatus.SERVICE_UNAVAILABLE,
      "BUSINESS_NUMBER_VERIFICATION_FAILED",
      "사업자 번호 검증에 실패했습니다. 잠시 후 다시 시도해 주세요"),
  ADDRESS_GEOCODING_FAILED(
      HttpStatus.BAD_REQUEST, "ADDRESS_GEOCODING_FAILED", "주소를 좌표로 변환할 수 없습니다"),
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
