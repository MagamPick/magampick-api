package com.magampick.point.domain;

/** 적립 lot 상태. */
public enum PointAccrualStatus {
  /** 잔여 포인트 있음. */
  ACTIVE,
  /** FIFO 차감으로 완전 소진. */
  EXHAUSTED,
  /** 유효기간 만료 소멸. */
  EXPIRED
}
