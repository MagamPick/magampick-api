package com.magampick.clearance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "마감 임박 상품 등록 요청")
public record ClearanceItemCreateRequest(
    @Schema(description = "원본 일반 상품 ID", example = "1") @NotNull Long productId,
    @Schema(description = "판매가 (원, 정수)", example = "3000")
        @NotNull
        @DecimalMin(value = "1")
        @Digits(integer = 12, fraction = 0)
        BigDecimal salePrice,
    @Schema(description = "등록 수량", example = "5") @NotNull @Min(1) Integer totalQuantity,
    @Schema(description = "픽업 종료 시각 (KST). 등록 당일만 허용", example = "2026-05-20T21:00:00") @NotNull
        LocalDateTime pickupEndAt) {}
