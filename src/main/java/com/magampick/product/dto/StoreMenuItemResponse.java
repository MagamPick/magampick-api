package com.magampick.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "소비자 메뉴 탭 — 판매중 상품 카드 (flat 리스트, FE 가 category 로 그룹화)")
public record StoreMenuItemResponse(
    @Schema(description = "상품 ID") Long id,
    @Schema(description = "상품 이름") String name,
    @Schema(description = "이미지 URL (없으면 null)") String imageUrl,
    @Schema(description = "정상가 (원)") BigDecimal price,
    @Schema(description = "카테고리 한국어 라벨", example = "베이커리") String category) {}
