package com.magampick.store.domain;

/** 매장 영업 상태. 전이 그래프 / 노출 룰은 노션 "매장 영업 상태 관리". */
public enum OperationStatus {
  /** 영업중 — 소비자 노출 O (단, 오늘 요일이 영업 요일일 때만). */
  OPEN,
  /** 잠시 휴식중 — 소비자 노출 X. */
  BREAK,
  /** 오늘 영업 종료 — 소비자 노출 X. 매장 등록 직후 초기값. */
  CLOSED_TODAY
}
