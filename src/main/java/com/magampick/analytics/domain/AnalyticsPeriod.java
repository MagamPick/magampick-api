package com.magampick.analytics.domain;

import com.magampick.analytics.exception.AnalyticsErrorCode;
import com.magampick.global.exception.BusinessException;

/** 통계 집계 기간 단위. */
public enum AnalyticsPeriod {
  TODAY,
  WEEK,
  MONTH,
  YEAR;

  /**
   * 소문자 문자열에서 변환. 매칭 실패 시 BusinessException(INVALID_PERIOD).
   *
   * @param raw "today" 등 소문자 문자열
   */
  public static AnalyticsPeriod from(String raw) {
    try {
      return valueOf(raw.toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new BusinessException(AnalyticsErrorCode.INVALID_PERIOD);
    }
  }
}
