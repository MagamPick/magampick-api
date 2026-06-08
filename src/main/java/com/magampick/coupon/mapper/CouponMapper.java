package com.magampick.coupon.mapper;

import com.magampick.coupon.domain.Coupon;
import com.magampick.coupon.domain.CouponStatus;
import com.magampick.coupon.domain.UserCoupon;
import com.magampick.coupon.dto.AdminCouponResponse;
import com.magampick.coupon.dto.CouponEventResponse;
import com.magampick.coupon.dto.CouponResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** 쿠폰 도메인 MapStruct 매퍼. */
@Mapper(componentModel = "spring")
public interface CouponMapper {

  /**
   * UserCoupon 인스턴스 + 표시 상태 → CouponResponse.
   *
   * <p>discountType / value / minOrder / label 은 uc.coupon 에서, expiresAt 은 uc.expiresAt 에서 가져온다.
   */
  @Mapping(target = "id", source = "uc.id")
  @Mapping(target = "status", source = "status")
  @Mapping(target = "discountType", source = "uc.coupon.discountType")
  @Mapping(target = "value", source = "uc.coupon.discountValue")
  @Mapping(target = "minOrder", source = "uc.coupon.minOrder")
  @Mapping(target = "label", source = "uc.coupon.label")
  @Mapping(target = "expiresAt", source = "uc.expiresAt")
  CouponResponse toResponse(UserCoupon uc, CouponStatus status);

  /**
   * Coupon 마스터 + claimed 플래그 → CouponEventResponse.
   *
   * <p>expiresAt = coupon.validUntil, value = coupon.discountValue.
   */
  @Mapping(target = "couponId", source = "coupon.id")
  @Mapping(target = "discountType", source = "coupon.discountType")
  @Mapping(target = "value", source = "coupon.discountValue")
  @Mapping(target = "minOrder", source = "coupon.minOrder")
  @Mapping(target = "label", source = "coupon.label")
  @Mapping(target = "expiresAt", source = "coupon.validUntil")
  @Mapping(target = "claimed", source = "claimed")
  CouponEventResponse toEventResponse(Coupon coupon, boolean claimed);

  /**
   * Coupon 마스터 → AdminCouponResponse.
   *
   * <p>value = coupon.discountValue.
   */
  @Mapping(target = "value", source = "discountValue")
  AdminCouponResponse toAdminResponse(Coupon coupon);
}
