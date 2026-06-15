package com.magampick.notification.dto;

import java.util.List;

/** 알림 목록 응답. */
public record NotificationListResponse(List<NotificationResponse> items) {}
