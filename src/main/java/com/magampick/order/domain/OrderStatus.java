package com.magampick.order.domain;

/** 주문 상태. 전이 로직은 Phase 5 구현 예정 — 현재는 스키마/엔티티만. */
public enum OrderStatus {
  RECEIVED,
  PREPARING,
  READY,
  PICKED_UP,
  CANCELED
}
