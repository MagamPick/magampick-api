package com.magampick.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "[임시] FCM 토큰 직접 발송 요청")
public record DevPushEchoRequest(
    @Schema(description = "FCM 디바이스 토큰") @NotBlank String token,
    @Schema(description = "알림 제목", example = "테스트 알림") @NotBlank String title,
    @Schema(description = "알림 본문", example = "FCM 배선 확인") @NotBlank String body) {}
