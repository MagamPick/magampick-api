package com.magampick.support.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** 문의 카테고리. 9개 값. 요청 본문 역직렬화(@JsonCreator) + 응답 직렬화(@JsonValue) 모두 소문자. */
public enum InquiryCategory {
  PAYMENT("payment"),
  ORDER("order"),
  COUPON("coupon"),
  ACCOUNT("account"),
  REPORT("report"),
  SETTLEMENT("settlement"),
  STORE("store"),
  PRODUCT("product"),
  ETC("etc");

  private final String value;

  InquiryCategory(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }

  /**
   * 소문자 문자열에서 InquiryCategory 역직렬화. 잘못된 값이면 IllegalArgumentException.
   *
   * @param value 소문자 카테고리 값
   * @return 매핑된 InquiryCategory
   */
  @JsonCreator
  public static InquiryCategory from(String value) {
    for (InquiryCategory category : values()) {
      if (category.value.equalsIgnoreCase(value)) {
        return category;
      }
    }
    throw new IllegalArgumentException("알 수 없는 InquiryCategory 값: " + value);
  }
}
