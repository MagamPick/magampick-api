package com.magampick.phone.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "휴대폰 인증번호 검증 요청")
public record PhoneVerificationConfirmRequest(
    @Schema(description = "휴대폰 번호", example = "010-1234-5678") @NotBlank String phone,
    @Schema(description = "6자리 인증번호", example = "123456")
        @NotBlank
        @Pattern(regexp = "^\\d{6}$", message = "인증번호는 6자리 숫자여야 합니다")
        String code) {}
