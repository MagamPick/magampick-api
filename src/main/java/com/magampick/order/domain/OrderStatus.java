package com.magampick.order.domain;

/** 주문 상태 7값. Phase 5A 에서 PENDING 으로만 생성, 이후 상태 전이는 5B/6. */
public enum OrderStatus {
  /** 주문접수 — 결제 완료, 사장 수락 전. Phase 5A 기본값. */
  PENDING,
  /** 준비중 — 사장이 수락 후 준비 시작. */
  PREPARING,
  /** 준비완료 — 픽업 가능 상태. */
  READY,
  /** 수령완료 — 소비자 픽업 완료. */
  COMPLETED,
  /** 미수령 — 픽업 시간 내 미수령. */
  NO_SHOW,
  /** 사장 거절 — 사장이 주문 거절. */
  REJECTED,
  /** 소비자 취소 — 소비자가 주문 취소. */
  CANCELLED
}
