package com.magampick.favorite.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "단골 매장 목록 아이템")
public record FavoriteStoreResponse(
    @Schema(description = "매장 ID", example = "1") Long id,
    @Schema(description = "매장명", example = "동네빵집") String name,
    @Schema(description = "매장 이미지 URL") String imageUrl,
    @Schema(description = "기본 주소지 기준 거리 (km)", example = "1.23") double distanceKm,
    @Schema(description = "매장 평균 별점", example = "4.5") double rating,
    @Schema(description = "활성 마감할인 개수", example = "3") long activeDealCount) {}
