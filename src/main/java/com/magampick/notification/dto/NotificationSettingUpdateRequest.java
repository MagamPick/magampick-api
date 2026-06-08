package com.magampick.notification.dto;

import jakarta.validation.constraints.NotNull;

/** 알림 설정 개별 항목 변경 요청. */
public record NotificationSettingUpdateRequest(@NotNull Boolean enabled) {}
