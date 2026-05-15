package com.magampick.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "카카오 로그인 요청(Mock)")
public record KakaoLoginRequest(
    @Schema(description = "카카오 액세스 토큰", example = "mock-kakao-token") @NotBlank
        String kakaoAccessToken) {}
