package com.magampick.store.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 소비자 매장 목록 아이템. */
@Schema(description = "매장 목록 아이템")
public record StoreListItemResponse(
    @Schema(description = "매장 ID") Long id,
    @Schema(description = "매장명") String name,
    @Schema(description = "대표 이미지 URL (null = 없음)") String imageUrl,
    @Schema(description = "직선 거리 (km)", example = "1.23") double distanceKm,
    @Schema(description = "평점 평균 (0.0 = 리뷰 없음)", example = "4.5") double rating,
    @Schema(description = "진행중 마감할인 개수", example = "3") long activeDealCount,
    @Schema(description = "단골 여부") boolean isFavorite) {}
