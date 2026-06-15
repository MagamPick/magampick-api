package com.magampick.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "비밀번호 재설정 본인확인 요청")
public record PasswordResetVerifyRequest(
    @Schema(description = "가입 이메일", example = "customer@magampick.com")
        @NotBlank
        @Email
        @Size(max = 255)
        String email,
    @Schema(description = "휴대폰 번호", example = "010-1234-5678") @NotBlank String phone,
    @Schema(description = "휴대폰 본인인증 검증 응답의 verificationToken", example = "a1b2c3d4-...") @NotBlank
        String verificationToken) {}
