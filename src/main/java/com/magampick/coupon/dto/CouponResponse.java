package com.magampick.coupon.dto;

import com.magampick.coupon.domain.CouponDiscountType;
import com.magampick.coupon.domain.CouponStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/** 소비자 쿠폰함 / 발급(claim) 결과 응답. */
public record CouponResponse(
    @Schema(description = "발급 인스턴스 ID") Long id,
    @Schema(description = "쿠폰 상태") CouponStatus status,
    @Schema(description = "할인 방식") CouponDiscountType discountType,
    @Schema(description = "할인 값 (RATE=%, AMOUNT=원)") int value,
    @Schema(description = "최소 주문 금액") int minOrder,
    @Schema(description = "쿠폰 이름") String label,
    @Schema(description = "유효기간 만료일") LocalDate expiresAt) {}
