package com.magampick.notification.domain;

/** 알림 카테고리. notifications.category 컬럼 값과 1:1 매핑. */
public enum NotificationCategory {
  DEAL,
  ORDER,
  REVIEW,
  BENEFIT,
  SYSTEM,
  REFUND,
  SETTLEMENT,
  NOTICE,
  INQUIRY
}
