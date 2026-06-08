package com.magampick.point.dto;

import com.magampick.point.domain.PointReason;
import java.util.EnumSet;
import java.util.Set;

/** 포인트 내역 조회 필터. FE pointDirection 파라미터에 대응. */
public enum PointHistoryFilter {
  /** 적립 내역 (EARN + RESTORE). */
  EARN,
  /** 사용/소멸 내역 (USE + EXPIRE + CLAWBACK). */
  USE,
  /** 전체 내역 (EARN + USE 의 합집합). */
  ALL;

  /**
   * 이 필터에 해당하는 포인트 사유 집합을 반환한다.
   *
   * <ul>
   *   <li>EARN → {EARN, RESTORE}
   *   <li>USE → {USE, EXPIRE, CLAWBACK}
   *   <li>ALL → EARN.reasons() ∪ USE.reasons()
   * </ul>
   */
  public Set<PointReason> reasons() {
    return switch (this) {
      case EARN -> EnumSet.of(PointReason.EARN, PointReason.RESTORE);
      case USE -> EnumSet.of(PointReason.USE, PointReason.EXPIRE, PointReason.CLAWBACK);
      case ALL -> {
        EnumSet<PointReason> all = EnumSet.copyOf(EARN.reasons());
        all.addAll(USE.reasons());
        yield all;
      }
    };
  }
}
