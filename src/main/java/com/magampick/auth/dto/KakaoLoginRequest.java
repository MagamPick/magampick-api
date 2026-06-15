package com.magampick.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "카카오 로그인 요청 (인가 코드)")
public record KakaoLoginRequest(
    @Schema(description = "카카오 인가 코드", example = "Q1w2E3r4t5...") @NotBlank
        String authorizationCode,
    @Schema(
            description = "인가 요청에 사용한 redirect URI (토큰 교환 시 동일 값 검증)",
            example = "https://magampick.com/login/kakao/callback")
        @NotBlank
        String redirectUri) {}
