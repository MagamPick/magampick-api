package com.magampick.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "토큰 발급 응답 (refresh 는 HttpOnly 쿠키로 전달되어 바디에 없음)")
public record TokenResponse(
    @Schema(description = "Access Token", example = "eyJhbGciOiJIUzI1NiJ9...") String accessToken,
    @Schema(description = "Access Token 만료까지 남은 초", example = "1800") long accessExpiresIn) {}
