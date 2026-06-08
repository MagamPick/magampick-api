package com.magampick.coupon.dto;

import com.magampick.coupon.domain.CouponDiscountType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;

/** 관리자 이벤트 쿠폰 생성 요청. issueLimit null = 무제한. */
public record AdminCouponCreateRequest(
    @Schema(description = "쿠폰 이름") @NotBlank String label,
    @Schema(description = "할인 방식") @NotNull CouponDiscountType discountType,
    @Schema(description = "할인 값 (RATE=1~100%, AMOUNT=원)") @Positive int value,
    @Schema(description = "최소 주문 금액") @PositiveOrZero int minOrder,
    @Schema(description = "고정 만료일") @NotNull @FutureOrPresent LocalDate validUntil,
    @Schema(description = "발급 한도 (null = 무제한)") @Positive Integer issueLimit) {}
