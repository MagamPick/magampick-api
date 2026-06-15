package com.magampick.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "FCM 토큰 등록 응답")
public record PushTokenResponse(@Schema(description = "등록된 토큰 ID", example = "100") Long id) {}
