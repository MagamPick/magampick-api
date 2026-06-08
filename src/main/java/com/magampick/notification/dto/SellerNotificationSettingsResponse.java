package com.magampick.notification.dto;

import com.magampick.notification.domain.SellerNotificationSetting;

/** 사장 알림 설정 응답. */
public record SellerNotificationSettingsResponse(
    boolean newOrder,
    boolean orderCancel,
    boolean refundRequest,
    boolean newReview,
    boolean notice,
    boolean marketing) {

  public static SellerNotificationSettingsResponse from(SellerNotificationSetting setting) {
    return new SellerNotificationSettingsResponse(
        setting.isNewOrder(),
        setting.isOrderCancel(),
        setting.isRefundRequest(),
        setting.isNewReview(),
        setting.isNotice(),
        setting.isMarketing());
  }
}
