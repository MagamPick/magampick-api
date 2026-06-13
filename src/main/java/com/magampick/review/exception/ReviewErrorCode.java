package com.magampick.review.exception;

import com.magampick.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 리뷰 도메인 에러 코드. */
@Getter
@RequiredArgsConstructor
public enum ReviewErrorCode implements BaseErrorCode {
  REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "REVIEW_NOT_FOUND", "리뷰를 찾을 수 없습니다"),
  REVIEW_FORBIDDEN(HttpStatus.FORBIDDEN, "REVIEW_FORBIDDEN", "본인 리뷰에만 접근할 수 있습니다"),
  REVIEW_ALREADY_EXISTS(HttpStatus.CONFLICT, "REVIEW_ALREADY_EXISTS", "이 주문에 이미 리뷰가 존재합니다"),
  REVIEW_LOCKED(HttpStatus.CONFLICT, "REVIEW_LOCKED", "사장 답글이 달린 리뷰는 수정·삭제할 수 없습니다"),
  REVIEW_NOT_ELIGIBLE(HttpStatus.CONFLICT, "REVIEW_NOT_ELIGIBLE", "픽업 완료된 주문만 리뷰를 작성할 수 있습니다"),
  REPLY_ALREADY_EXISTS(HttpStatus.CONFLICT, "REPLY_ALREADY_EXISTS", "이미 답글이 등록된 리뷰입니다"),
  REPLY_STORE_FORBIDDEN(HttpStatus.FORBIDDEN, "REPLY_STORE_FORBIDDEN", "본인 매장 리뷰에만 답글을 달 수 있습니다"),
  REVIEW_IMAGE_TOO_MANY(HttpStatus.BAD_REQUEST, "REVIEW_IMAGE_TOO_MANY", "사진은 최대 3장까지 첨부할 수 있습니다"),
  REVIEW_IMAGE_TOO_LARGE(
      HttpStatus.BAD_REQUEST, "REVIEW_IMAGE_TOO_LARGE", "이미지 파일은 최대 5MB까지 업로드할 수 있습니다"),
  REVIEW_IMAGE_INVALID_TYPE(
      HttpStatus.BAD_REQUEST, "REVIEW_IMAGE_INVALID_TYPE", "jpg, png, webp 형식의 이미지만 업로드할 수 있습니다"),
  REVIEW_IMAGE_UPLOAD_FAILED(
      HttpStatus.INTERNAL_SERVER_ERROR, "REVIEW_IMAGE_UPLOAD_FAILED", "리뷰 이미지 업로드에 실패했습니다"),
  ;

  private final HttpStatus status;
  private final String code;
  private final String message;
}
