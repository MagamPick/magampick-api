package com.magampick.store.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 우리 동네 마감픽 매장 카드 응답. GET /api/v1/stores/neighborhood 결과 단건. */
@Schema(description = "우리 동네 마감픽 매장 카드")
public record NeighborhoodStoreResponse(
    @Schema(description = "매장 ID") Long id,
    @Schema(description = "매장명") String name,
    @Schema(description = "매장 대표 이미지 URL (없으면 null)", nullable = true) String imageUrl,
    @Schema(description = "거리 (km, 기본 주소지 기준)") double distanceKm,
    @Schema(description = "평균 별점 (0.0 = 리뷰 없음)") double rating,
    @Schema(description = "진행중 마감할인 개수") long activeDealCount) {}
