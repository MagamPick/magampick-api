package com.magampick.clearance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "소비자 마감할인 탭 — 활성 떨이 카드")
public record StoreDealResponse(
    @Schema(description = "떨이 ID") Long id,
    @Schema(description = "떨이 이름") String name,
    @Schema(description = "상품 이미지 URL (없으면 null)") String imageUrl,
    @Schema(description = "할인율 (%)", example = "30") int discountRate,
    @Schema(description = "정상가 (원)") BigDecimal originalPrice,
    @Schema(description = "판매가 (원)") BigDecimal salePrice,
    @Schema(description = "픽업 마감 일시 (ISO-8601)") LocalDateTime pickupDeadline,
    @Schema(description = "잔여 수량") int stockLeft) {}
