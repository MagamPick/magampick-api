package com.magampick.search.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 검색 결과 상품 아이템의 공통 필드 (비-discriminated 베이스 스키마). {@link SearchProductItemResponse} 의 자식(Deal/Menu)이
 * allOf 로 상속하는 대상 — union(부모) 대신 이 베이스만 상속해 순환 참조를 끊는다.
 *
 * <p>OpenAPI 스키마 정의 전용. 코드에서 직접 인스턴스화하지 않으며, 와이어 포맷은 각 자식 record 가 동일 필드를 직렬화한다.
 */
@Schema(description = "검색 결과 상품 아이템 공통 필드")
public record SearchProductItemBase(
    @Schema(description = "kind 판별자 (deal | menu)") String kind,
    @Schema(description = "매장 ID") Long storeId,
    @Schema(description = "매장명") String storeName,
    @Schema(description = "상품명") String name,
    @Schema(description = "이미지 URL (nullable)") String imageUrl) {}
