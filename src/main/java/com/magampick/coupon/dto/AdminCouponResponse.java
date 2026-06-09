package com.magampick.coupon.dto;

import com.magampick.coupon.domain.CouponDiscountType;
import com.magampick.coupon.domain.EventStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/** 관리자 이벤트 쿠폰 생성/조회 응답. */
public record AdminCouponResponse(
    @Schema(description = "쿠폰 마스터 ID") Long id,
    @Schema(description = "쿠폰 이름") String label,
    @Schema(description = "할인 방식") CouponDiscountType discountType,
    @Schema(description = "할인 값") int value,
    @Schema(description = "최소 주문 금액") int minOrder,
    @Schema(description = "고정 만료일") LocalDate validUntil,
    @Schema(description = "발급 한도 (null = 무제한)") Integer issueLimit,
    @Schema(description = "누적 발급 수") int issuedCount,
    @Schema(description = "활성 여부") boolean active,
    @Schema(description = "이벤트 노출 시작일") LocalDate displayStartAt,
    @Schema(description = "이벤트 노출 종료일") LocalDate displayEndAt,
    @Schema(description = "이벤트 상태 (scheduled/ongoing/ended)") EventStatus status) {}
