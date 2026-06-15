package com.magampick.address.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(description = "주소지 응답")
public record AddressResponse(
    @Schema(description = "주소지 식별자", example = "1") Long id,
    @Schema(description = "사용자 지정 라벨", example = "집") String label,
    @Schema(description = "도로명 주소", example = "서울특별시 강남구 테헤란로 427") String roadAddress,
    @Schema(description = "지번 주소", example = "서울특별시 강남구 삼성동 159") String jibunAddress,
    @Schema(description = "상세 주소", example = "101동 1502호") String detailAddress,
    @Schema(description = "우편번호 5자리", example = "06158") String zonecode,
    @Schema(description = "위도", example = "37.5066") Double latitude,
    @Schema(description = "경도", example = "127.0535") Double longitude,
    @Schema(description = "기본 주소지 여부", example = "true") boolean isDefault,
    @Schema(description = "생성 시각 (KST)") OffsetDateTime createdAt,
    @Schema(description = "수정 시각 (KST)") OffsetDateTime updatedAt) {}
