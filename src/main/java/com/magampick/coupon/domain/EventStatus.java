package com.magampick.coupon.domain;

import com.fasterxml.jackson.annotation.JsonValue;

/** 이벤트 쿠폰 노출 상태. 저장하지 않고 조회 시 도출한다. */
public enum EventStatus {
  SCHEDULED("scheduled"),
  ONGOING("ongoing"),
  ENDED("ended");

  private final String value;

  EventStatus(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }
}
