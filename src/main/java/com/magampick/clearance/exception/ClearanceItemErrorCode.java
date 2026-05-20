package com.magampick.clearance.exception;

import com.magampick.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 마감 임박 상품 도메인 에러 코드. */
@Getter
@RequiredArgsConstructor
public enum ClearanceItemErrorCode implements BaseErrorCode {
  CLEARANCE_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "CLEARANCE_ITEM_NOT_FOUND", "마감 임박 상품을 찾을 수 없습니다"),
  CLEARANCE_ITEM_OPEN_EXISTS(
      HttpStatus.CONFLICT, "CLEARANCE_ITEM_OPEN_EXISTS", "해당 상품에 이미 진행 중인 마감 임박 상품이 있습니다"),
  CLEARANCE_ITEM_PRODUCT_NOT_ON_SALE(
      HttpStatus.BAD_REQUEST,
      "CLEARANCE_ITEM_PRODUCT_NOT_ON_SALE",
      "판매 중인 상품만 마감 임박 상품으로 등록할 수 있습니다"),
  CLEARANCE_ITEM_SALE_PRICE_NOT_DISCOUNTED(
      HttpStatus.BAD_REQUEST, "CLEARANCE_ITEM_SALE_PRICE_NOT_DISCOUNTED", "판매가는 정상가보다 낮아야 합니다"),
  CLEARANCE_ITEM_INVALID_PICKUP_WINDOW(
      HttpStatus.BAD_REQUEST,
      "CLEARANCE_ITEM_INVALID_PICKUP_WINDOW",
      "픽업 시간은 오늘 내이어야 하며, 시작 시각이 종료 시각보다 앞서야 합니다"),
  ;

  private final HttpStatus status;
  private final String code;
  private final String message;
}
