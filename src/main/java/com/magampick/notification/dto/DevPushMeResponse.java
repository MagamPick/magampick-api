package com.magampick.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "[임시] 내 토큰 발송 응답")
public record DevPushMeResponse(
    @Schema(description = "발송 성공한 토큰 수", example = "2") int sentCount) {}
