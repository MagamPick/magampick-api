package com.magampick.coupon.dto;

import com.magampick.coupon.domain.CouponDiscountType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/**
 * 관리자 이벤트 쿠폰 부분 수정 요청 (PATCH). null 필드는 수정하지 않는다.
 *
 * <p>이미 발급된 UserCoupon 의 스냅샷은 변경되지 않는다 — 마스터 수정은 미래 발급분에만 영향.
 */
public record AdminCouponUpdateRequest(
    @Schema(description = "쿠폰 이름 (null=미수정)") String label,
    @Schema(description = "할인 방식 (null=미수정)") CouponDiscountType discountType,
    @Schema(description = "할인 값 (null=미수정)") Integer discountValue,
    @Schema(description = "최소 주문 금액 (null=미수정)") Integer minOrder,
    @Schema(description = "고정 만료일 (null=미수정)") LocalDate validUntil,
    @Schema(description = "발급 한도 (null=미수정)") Integer issueLimit,
    @Schema(description = "이벤트 노출 시작일 (null=미수정)") LocalDate displayStartAt,
    @Schema(description = "이벤트 노출 종료일 (null=미수정)") LocalDate displayEndAt) {}
