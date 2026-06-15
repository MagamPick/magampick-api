package com.magampick.coupon.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** UserCoupon 스냅샷 기반 할인 메서드 단위 테스트. */
class UserCouponTest {

  private UserCoupon buildUserCoupon(
      CouponDiscountType discountType, int discountValue, int minOrder) {
    return UserCoupon.builder()
        .discountType(discountType)
        .discountValue(discountValue)
        .minOrder(minOrder)
        .status(CouponStatus.USABLE)
        .expiresAt(LocalDate.now().plusDays(30))
        .issuedAt(LocalDateTime.now())
        .build();
  }

  // ── isApplicableTo (스냅샷 기반) ─────────────────────────────────────────────

  @Test
  void isApplicableTo_최소주문_충족_true() {
    UserCoupon uc = buildUserCoupon(CouponDiscountType.AMOUNT, 1000, 5000);
    assertThat(uc.isApplicableTo(new BigDecimal("5000"))).isTrue();
    assertThat(uc.isApplicableTo(new BigDecimal("6000"))).isTrue();
  }

  @Test
  void isApplicableTo_최소주문_미달_false() {
    UserCoupon uc = buildUserCoupon(CouponDiscountType.AMOUNT, 1000, 5000);
    assertThat(uc.isApplicableTo(new BigDecimal("4999"))).isFalse();
  }

  @Test
  void isApplicableTo_0원_false() {
    UserCoupon uc = buildUserCoupon(CouponDiscountType.AMOUNT, 1000, 0);
    assertThat(uc.isApplicableTo(BigDecimal.ZERO)).isFalse();
  }

  // ── calcDiscount (스냅샷 기반) ───────────────────────────────────────────────

  @Test
  void calcDiscount_RATE_floor() {
    // 스냅샷 RATE 10%, 9999 → 999.9 → floor → 999
    UserCoupon uc = buildUserCoupon(CouponDiscountType.RATE, 10, 0);
    assertThat(uc.calcDiscount(new BigDecimal("9999"))).isEqualByComparingTo("999");
  }

  @Test
  void calcDiscount_AMOUNT_정액() {
    // 스냅샷 AMOUNT 3000, menuSubtotal 10000 → 3000
    UserCoupon uc = buildUserCoupon(CouponDiscountType.AMOUNT, 3000, 0);
    assertThat(uc.calcDiscount(new BigDecimal("10000"))).isEqualByComparingTo("3000");
  }

  @Test
  void calcDiscount_AMOUNT_min_초과방지() {
    // 스냅샷 AMOUNT 3000 > menuSubtotal 2000 → min = 2000
    UserCoupon uc = buildUserCoupon(CouponDiscountType.AMOUNT, 3000, 0);
    assertThat(uc.calcDiscount(new BigDecimal("2000"))).isEqualByComparingTo("2000");
  }

  // ── 소급 방지 회귀 테스트 ─────────────────────────────────────────────────────

  @Test
  void 스냅샷_기반_소급_방지_마스터_변경_무관() {
    // given — 발급 시점 스냅샷: AMOUNT 1000, minOrder 2000
    UserCoupon uc = buildUserCoupon(CouponDiscountType.AMOUNT, 1000, 2000);

    // 스냅샷 기반 계산 — 마스터가 변경되어도 이 객체의 스냅샷은 그대로
    assertThat(uc.isApplicableTo(new BigDecimal("3000"))).isTrue();
    assertThat(uc.calcDiscount(new BigDecimal("3000"))).isEqualByComparingTo("1000");

    // 마스터가 나중에 AMOUNT 2000 으로 변경됐다고 가정해도 uc 는 여전히 1000 반환
    // (스냅샷이 있으므로 새 UserCoupon 을 만들어야만 새 값이 반영됨)
    UserCoupon ucWithNewMaster = buildUserCoupon(CouponDiscountType.AMOUNT, 2000, 2000);
    assertThat(ucWithNewMaster.calcDiscount(new BigDecimal("3000"))).isEqualByComparingTo("2000");

    // 기존 uc 는 변경 없음
    assertThat(uc.calcDiscount(new BigDecimal("3000"))).isEqualByComparingTo("1000");
  }
}
