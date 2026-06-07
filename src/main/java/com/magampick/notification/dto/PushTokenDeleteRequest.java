package com.magampick.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "FCM 토큰 해제 요청")
public record PushTokenDeleteRequest(
    @Schema(description = "해제할 FCM 디바이스 토큰") @NotBlank String token) {}
