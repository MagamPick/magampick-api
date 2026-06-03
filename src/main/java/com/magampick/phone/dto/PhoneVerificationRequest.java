package com.magampick.phone.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "휴대폰 인증번호 발송 요청")
public record PhoneVerificationRequest(
    @Schema(description = "휴대폰 번호", example = "010-1234-5678") @NotBlank String phone) {}
