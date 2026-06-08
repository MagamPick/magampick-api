package com.magampick.coupon.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Coupon 도메인 메서드 단위 테스트. */
class CouponTest {

  private Coupon buildRateCoupon(int ratePercent, int minOrder) {
    return Coupon.builder()
        .kind(CouponKind.EVENT)
        .label("RATE 쿠폰")
        .discountType(CouponDiscountType.RATE)
        .discountValue(ratePercent)
        .minOrder(minOrder)
        .validUntil(LocalDate.now().plusDays(30))
        .active(true)
        .build();
  }

  private Coupon buildAmountCoupon(int amount, int minOrder) {
    return Coupon.builder()
        .kind(CouponKind.EVENT)
        .label("AMOUNT 쿠폰")
        .discountType(CouponDiscountType.AMOUNT)
        .discountValue(amount)
        .minOrder(minOrder)
        .validUntil(LocalDate.now().plusDays(30))
        .active(true)
        .build();
  }

  // ── isApplicableTo ────────────────────────────────────────────────────────────

  @Test
  void isApplicableTo_최소주문_충족_true() {
    Coupon coupon = buildRateCoupon(10, 5000);
    assertThat(coupon.isApplicableTo(new BigDecimal("5000"))).isTrue();
    assertThat(coupon.isApplicableTo(new BigDecimal("6000"))).isTrue();
  }

  @Test
  void isApplicableTo_최소주문_미달_false() {
    Coupon coupon = buildRateCoupon(10, 5000);
    assertThat(coupon.isApplicableTo(new BigDecimal("4999"))).isFalse();
  }

  @Test
  void isApplicableTo_0원_false() {
    Coupon coupon = buildRateCoupon(10, 0);
    assertThat(coupon.isApplicableTo(BigDecimal.ZERO)).isFalse();
  }

  // ── calcDiscount ─────────────────────────────────────────────────────────────

  @Test
  void calcDiscount_RATE_floor() {
    // 10% of 9999 = 999.9 → floor → 999
    Coupon coupon = buildRateCoupon(10, 0);
    BigDecimal discount = coupon.calcDiscount(new BigDecimal("9999"));
    assertThat(discount).isEqualByComparingTo(new BigDecimal("999"));
  }

  @Test
  void calcDiscount_RATE_딱_떨어지는_경우() {
    // 20% of 5000 = 1000
    Coupon coupon = buildRateCoupon(20, 0);
    BigDecimal discount = coupon.calcDiscount(new BigDecimal("5000"));
    assertThat(discount).isEqualByComparingTo(new BigDecimal("1000"));
  }

  @Test
  void calcDiscount_AMOUNT_min() {
    // 할인액 3000 > menuSubtotal 2500 → min = 2500 (menuSubtotal 초과 할인 방지)
    Coupon coupon = buildAmountCoupon(3000, 0);
    BigDecimal discount = coupon.calcDiscount(new BigDecimal("2500"));
    assertThat(discount).isEqualByComparingTo(new BigDecimal("2500"));
  }

  @Test
  void calcDiscount_AMOUNT_정액() {
    // 할인액 3000 ≤ menuSubtotal 10000 → 3000 반환
    Coupon coupon = buildAmountCoupon(3000, 0);
    BigDecimal discount = coupon.calcDiscount(new BigDecimal("10000"));
    assertThat(discount).isEqualByComparingTo(new BigDecimal("3000"));
  }

  @Test
  void calcDiscount_RATE_100초과_menuSubtotal_상한() {
    // discountValue=200 (200%) 이라도 menuSubtotal=5000 을 초과할 수 없음 → 5000
    Coupon coupon = buildRateCoupon(200, 0);
    BigDecimal discount = coupon.calcDiscount(new BigDecimal("5000"));
    assertThat(discount).isEqualByComparingTo(new BigDecimal("5000"));
  }
}
