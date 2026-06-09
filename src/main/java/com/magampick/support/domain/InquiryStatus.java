package com.magampick.support.domain;

import com.fasterxml.jackson.annotation.JsonValue;

/** 문의 상태. 응답 직렬화 전용 (@JsonValue 소문자). */
public enum InquiryStatus {
  PENDING("pending"),
  ANSWERED("answered");

  private final String value;

  InquiryStatus(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }
}
