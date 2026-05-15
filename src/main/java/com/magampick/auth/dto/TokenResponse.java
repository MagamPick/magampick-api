package com.magampick.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "토큰 발급 응답")
public record TokenResponse(
    @Schema(description = "Access Token", example = "eyJhbGciOiJIUzI1NiJ9...") String accessToken,
    @Schema(description = "Refresh Token", example = "eyJhbGciOiJIUzI1NiJ9...") String refreshToken,
    @Schema(description = "Access Token 만료까지 남은 초", example = "1800") long accessExpiresIn) {}
