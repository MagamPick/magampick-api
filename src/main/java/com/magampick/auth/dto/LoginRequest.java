package com.magampick.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "로그인 요청")
public record LoginRequest(
    @Schema(description = "이메일", example = "user@magampick.com") @NotBlank @Email @Size(max = 255)
        String email,
    @Schema(description = "비밀번호", example = "Abcd1234!") @NotBlank String password) {}
