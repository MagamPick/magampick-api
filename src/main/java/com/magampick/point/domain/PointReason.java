package com.magampick.point.domain;

/** 포인트 내역 사유 (5종). */
public enum PointReason {
  /** 구매 적립. */
  EARN,
  /** 포인트 사용. */
  USE,
  /** 유효기간 만료 소멸. */
  EXPIRE,
  /** 취소/환불로 인한 복원. */
  RESTORE,
  /** 비정상 거래 회수. */
  CLAWBACK
}
