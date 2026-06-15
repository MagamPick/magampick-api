package com.magampick.coupon.dto;

import com.magampick.coupon.domain.CouponDiscountType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/** 이벤트 쿠폰 목록 응답. claimed = 해당 소비자가 이미 받았는지 여부. */
public record CouponEventResponse(
    @Schema(description = "쿠폰 마스터 ID") Long couponId,
    @Schema(description = "할인 방식") CouponDiscountType discountType,
    @Schema(description = "할인 값 (RATE=%, AMOUNT=원)") int value,
    @Schema(description = "최소 주문 금액") int minOrder,
    @Schema(description = "쿠폰 이름") String label,
    @Schema(description = "만료일 (마스터 valid_until)") LocalDate expiresAt,
    @Schema(description = "이미 발급 받았으면 true") boolean claimed) {}
