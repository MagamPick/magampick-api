package com.magampick.order.domain;

/** 주문 상태 8값. 결제 대기(AWAITING_PAYMENT) → 결제 확인 후 PENDING. */
public enum OrderStatus {
  /** 결제 대기 — 주문 임시 저장, 토스 결제 확인 전. */
  AWAITING_PAYMENT,
  /** 주문접수 — 결제 완료, 사장 수락 전. */
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
