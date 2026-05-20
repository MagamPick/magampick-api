package com.magampick.clearance.dto;

import com.magampick.clearance.domain.ClearanceItemStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Schema(description = "마감 임박 상품 응답")
public record ClearanceItemResponse(
    @Schema(description = "마감 임박 상품 ID", example = "1") Long id,
    @Schema(description = "원본 상품 ID", example = "5") Long productId,
    @Schema(description = "상품 대표 이미지 URL (원본 상품 기준)") String imageUrl,
    @Schema(description = "상품명 (등록 시점 스냅샷)", example = "크로아상") String name,
    @Schema(description = "정상가 (원, 등록 시점 스냅샷)", example = "4500") BigDecimal regularPrice,
    @Schema(description = "판매가 (원)", example = "3000") BigDecimal salePrice,
    @Schema(description = "할인율 (0~1 사이 소수, 소수점 2자리)", example = "0.33") BigDecimal discountRate,
    @Schema(description = "등록 수량", example = "5") int totalQuantity,
    @Schema(description = "잔여 수량", example = "3") int remainingQuantity,
    @Schema(description = "픽업 시작 시각") OffsetDateTime pickupStartAt,
    @Schema(description = "픽업 종료 시각") OffsetDateTime pickupEndAt,
    @Schema(description = "상품 상태") ClearanceItemStatus status,
    @Schema(description = "등록 시각") OffsetDateTime createdAt) {}
