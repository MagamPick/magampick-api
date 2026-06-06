package com.magampick.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "비밀번호 재설정 본인확인 응답")
public record PasswordResetVerifyResponse(
    @Schema(description = "비밀번호 재설정 토큰", example = "a1b2c3d4-...") String resetToken) {}
