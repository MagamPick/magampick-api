package com.magampick.coupon.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Coupon 도메인 메서드 단위 테스트. */
class CouponTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 6, 8); // 고정 기준일

  private Coupon buildRateCoupon(int ratePercent, int minOrder) {
    return Coupon.builder()
        .kind(CouponKind.EVENT)
        .label("RATE 쿠폰")
        .discountType(CouponDiscountType.RATE)
        .discountValue(ratePercent)
        .minOrder(minOrder)
        .validUntil(LocalDate.now().plusDays(30))
        .displayStartAt(TODAY.minusDays(7))
        .displayEndAt(TODAY.plusDays(30))
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
        .displayStartAt(TODAY.minusDays(7))
        .displayEndAt(TODAY.plusDays(30))
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

  // ── eventStatus ──────────────────────────────────────────────────────────────

  @Test
  void eventStatus_active_false이면_ENDED() {
    Coupon coupon =
        Coupon.builder()
            .kind(CouponKind.EVENT)
            .label("종료 쿠폰")
            .discountType(CouponDiscountType.AMOUNT)
            .discountValue(1000)
            .minOrder(0)
            .displayStartAt(TODAY.minusDays(10))
            .displayEndAt(TODAY.plusDays(10))
            .active(false) // inactive
            .build();
    assertThat(coupon.eventStatus(TODAY)).isEqualTo(EventStatus.ENDED);
    assertThat(coupon.isOngoing(TODAY)).isFalse();
  }

  @Test
  void eventStatus_today_before_displayStartAt이면_SCHEDULED() {
    Coupon coupon =
        Coupon.builder()
            .kind(CouponKind.EVENT)
            .label("예정 쿠폰")
            .discountType(CouponDiscountType.AMOUNT)
            .discountValue(1000)
            .minOrder(0)
            .displayStartAt(TODAY.plusDays(1)) // 내일부터
            .displayEndAt(TODAY.plusDays(30))
            .active(true)
            .build();
    assertThat(coupon.eventStatus(TODAY)).isEqualTo(EventStatus.SCHEDULED);
    assertThat(coupon.isOngoing(TODAY)).isFalse();
  }

  @Test
  void eventStatus_today_after_displayEndAt이면_ENDED() {
    Coupon coupon =
        Coupon.builder()
            .kind(CouponKind.EVENT)
            .label("종료 쿠폰")
            .discountType(CouponDiscountType.AMOUNT)
            .discountValue(1000)
            .minOrder(0)
            .displayStartAt(TODAY.minusDays(30))
            .displayEndAt(TODAY.minusDays(1)) // 어제 종료
            .active(true)
            .build();
    assertThat(coupon.eventStatus(TODAY)).isEqualTo(EventStatus.ENDED);
    assertThat(coupon.isOngoing(TODAY)).isFalse();
  }

  @Test
  void eventStatus_기간_내이면_ONGOING() {
    Coupon coupon =
        Coupon.builder()
            .kind(CouponKind.EVENT)
            .label("진행중 쿠폰")
            .discountType(CouponDiscountType.AMOUNT)
            .discountValue(1000)
            .minOrder(0)
            .displayStartAt(TODAY.minusDays(7))
            .displayEndAt(TODAY.plusDays(7))
            .active(true)
            .build();
    assertThat(coupon.eventStatus(TODAY)).isEqualTo(EventStatus.ONGOING);
    assertThat(coupon.isOngoing(TODAY)).isTrue();
  }

  @Test
  void eventStatus_displayStartAt_당일이면_ONGOING() {
    Coupon coupon =
        Coupon.builder()
            .kind(CouponKind.EVENT)
            .label("당일 시작 쿠폰")
            .discountType(CouponDiscountType.AMOUNT)
            .discountValue(1000)
            .minOrder(0)
            .displayStartAt(TODAY) // 오늘 시작
            .displayEndAt(TODAY.plusDays(30))
            .active(true)
            .build();
    assertThat(coupon.eventStatus(TODAY)).isEqualTo(EventStatus.ONGOING);
  }

  @Test
  void eventStatus_displayEndAt_당일이면_ONGOING() {
    Coupon coupon =
        Coupon.builder()
            .kind(CouponKind.EVENT)
            .label("당일 종료 쿠폰")
            .discountType(CouponDiscountType.AMOUNT)
            .discountValue(1000)
            .minOrder(0)
            .displayStartAt(TODAY.minusDays(30))
            .displayEndAt(TODAY) // 오늘 종료
            .active(true)
            .build();
    assertThat(coupon.eventStatus(TODAY)).isEqualTo(EventStatus.ONGOING);
  }

  @Test
  void eventStatus_displayStart_null이면_조건없이_시작() {
    Coupon coupon =
        Coupon.builder()
            .kind(CouponKind.EVENT)
            .label("시작일 없음")
            .discountType(CouponDiscountType.AMOUNT)
            .discountValue(1000)
            .minOrder(0)
            .displayStartAt(null) // null = 즉시 시작
            .displayEndAt(TODAY.plusDays(10))
            .active(true)
            .build();
    assertThat(coupon.eventStatus(TODAY)).isEqualTo(EventStatus.ONGOING);
  }
}
