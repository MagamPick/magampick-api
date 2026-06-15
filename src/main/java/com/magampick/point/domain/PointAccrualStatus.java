package com.magampick.point.domain;

/** 적립 lot 상태. */
public enum PointAccrualStatus {
  /** 환불 윈도우(3일) 종료 전 — 사용 불가. confirm 배치가 ACTIVE 로 전이한다. */
  PENDING,
  /** 잔여 포인트 있음 — 사용 가능. */
  ACTIVE,
  /** FIFO 차감으로 완전 소진. */
  EXHAUSTED,
  /** 유효기간 만료 소멸. */
  EXPIRED
}
