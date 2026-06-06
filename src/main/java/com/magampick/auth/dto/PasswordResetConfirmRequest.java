package com.magampick.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "비밀번호 재설정 완료 요청")
public record PasswordResetConfirmRequest(
    @Schema(description = "본인확인 후 발급받은 재설정 토큰", example = "a1b2c3d4-...") @NotBlank
        String resetToken,
    @Schema(description = "새 비밀번호", example = "Newpass123!")
        @NotBlank
        @Size(min = 8, max = 72)
        @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$",
            message = "비밀번호는 영문, 숫자, 특수문자를 각각 1개 이상 포함해야 합니다")
        String newPassword) {}
