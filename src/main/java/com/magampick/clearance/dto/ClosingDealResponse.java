package com.magampick.clearance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 마감 임박 특가 카드 응답. GET /api/v1/clearance-items/closing-soon 결과 단건. */
@Schema(description = "마감 임박 특가 떨이 카드")
public record ClosingDealResponse(
    @Schema(description = "떨이 상품 ID") Long id,
    @Schema(description = "매장명") String storeName,
    @Schema(description = "상품명 (떨이 이름)") String productName,
    @Schema(description = "상품 이미지 URL (없으면 null)", nullable = true) String imageUrl,
    @Schema(description = "할인율 (정수 %, 반올림)") int discountRate,
    @Schema(description = "정가") BigDecimal originalPrice,
    @Schema(description = "할인가") BigDecimal salePrice,
    @Schema(description = "픽업 마감 시각 (FE 카운트다운 기준)") LocalDateTime pickupDeadline) {}
