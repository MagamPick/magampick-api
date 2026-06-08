package com.magampick.notification.dto;

import com.magampick.notification.domain.CustomerNotificationSetting;

/** 소비자 알림 설정 응답. */
public record CustomerNotificationSettingsResponse(
    boolean nearbyDeal,
    boolean favoriteStore,
    boolean orderRefund,
    boolean reviewReply,
    boolean eventBenefit,
    boolean marketing) {

  public static CustomerNotificationSettingsResponse from(CustomerNotificationSetting setting) {
    return new CustomerNotificationSettingsResponse(
        setting.isNearbyDeal(),
        setting.isFavoriteStore(),
        setting.isOrderRefund(),
        setting.isReviewReply(),
        setting.isEventBenefit(),
        setting.isMarketing());
  }
}
