package com.magampick.seller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(description = "사장 프로필 응답")
public record SellerProfileResponse(
    @Schema(description = "사장 식별자", example = "1") Long id,
    @Schema(description = "로그인 이메일", example = "seller@example.com") String email,
    @Schema(description = "사장 이름", example = "홍길동") String ownerName,
    @Schema(description = "사업자번호 10자리", example = "1234567890") String businessNumber,
    @Schema(description = "휴대폰 번호. 가입 직후엔 null 가능", example = "01012345678") String phone,
    @Schema(description = "휴대폰 인증/변경 시각") OffsetDateTime phoneVerifiedAt,
    @Schema(
            description = "인증 상태",
            example = "APPROVED",
            allowableValues = {"PENDING", "APPROVED", "REJECTED"})
        String verificationStatus,
    @Schema(description = "가입 시각") OffsetDateTime createdAt) {}
