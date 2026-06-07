package com.magampick.payment.domain;

/** 결제 상태. Phase 5A stub 은 APPROVED 만 발생. */
public enum PaymentStatus {
  /** 승인 완료. */
  APPROVED,
  /** 승인 실패. */
  FAILED,
  /** 취소/환불. */
  CANCELED
}
