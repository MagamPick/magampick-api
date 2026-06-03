package com.magampick.phone.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "본인인증 토큰 응답")
public record PhoneVerificationTokenResponse(
    @Schema(
            description = "본인인증 토큰. 회원가입·비밀번호 재설정에서 사용하며 15분 후 만료된다.",
            example = "a1b2c3d4-5e6f-7a8b-9c0d-1e2f3a4b5c6d")
        String verificationToken) {}
