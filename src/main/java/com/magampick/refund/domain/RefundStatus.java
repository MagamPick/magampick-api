package com.magampick.refund.domain;

/** 환불 상태. */
public enum RefundStatus {
  /** 환불 요청됨 — 소비자 요청 후 사장 처리 대기. */
  REQUESTED,
  /** 환불 승인됨 — 사장이 승인하거나 자동 승인 배치 처리. */
  APPROVED,
  /** 환불 거부됨 — 사장이 사유와 함께 거부. */
  REJECTED
}
