package com.magampick.clearance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "마감 임박 상품 수정 요청 (null 필드는 변경 없음)")
public record ClearanceItemUpdateRequest(
    @Schema(description = "판매가 (원, 정수). null 이면 변경 없음", example = "2500")
        @DecimalMin(value = "1")
        @Digits(integer = 12, fraction = 0)
        BigDecimal salePrice,
    @Schema(description = "등록 수량. null 이면 변경 없음", example = "3") @Min(1) Integer totalQuantity,
    @Schema(description = "픽업 시작 시각 (KST). null 이면 변경 없음", example = "2026-05-20T17:00:00")
        LocalDateTime pickupStartAt,
    @Schema(description = "픽업 종료 시각 (KST). null 이면 변경 없음", example = "2026-05-20T21:00:00")
        LocalDateTime pickupEndAt) {}
