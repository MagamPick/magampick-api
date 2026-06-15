package com.magampick.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "[임시] FCM 발송 응답")
public record DevPushResponse(
    @Schema(description = "FCM 메시지 ID (mock 모드면 MOCK)", example = "projects/x/messages/1")
        String messageId) {}
