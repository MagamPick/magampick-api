package com.magampick.customer.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(description = "소비자 프로필 응답")
public record CustomerProfileResponse(
    @Schema(description = "소비자 식별자", example = "1") Long id,
    @Schema(description = "로그인 이메일", example = "customer@example.com") String email,
    @Schema(description = "닉네임", example = "마감픽유저") String nickname,
    @Schema(description = "휴대폰 번호. 가입 직후엔 null 가능", example = "01012345678") String phone,
    @Schema(description = "휴대폰 인증/변경 시각") OffsetDateTime phoneVerifiedAt,
    @Schema(description = "가입 시각") OffsetDateTime createdAt) {}
