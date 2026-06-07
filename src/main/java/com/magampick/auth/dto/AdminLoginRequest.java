package com.magampick.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "관리자 로그인 요청")
public record AdminLoginRequest(
    @Schema(description = "사용자명", example = "admin") @NotBlank @Size(max = 50) String username,
    @Schema(description = "비밀번호", example = "Admin1234!") @NotBlank String password) {}
