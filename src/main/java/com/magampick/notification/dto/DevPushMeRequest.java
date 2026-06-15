package com.magampick.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "[임시] 내 토큰으로 발송 요청")
public record DevPushMeRequest(
    @Schema(description = "알림 제목", example = "테스트 알림") @NotBlank String title,
    @Schema(description = "알림 본문", example = "내 토큰 발송 확인") @NotBlank String body) {}
