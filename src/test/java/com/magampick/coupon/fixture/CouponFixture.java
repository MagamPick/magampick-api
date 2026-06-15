package com.magampick.coupon.fixture;

import com.magampick.coupon.domain.Coupon;
import com.magampick.coupon.domain.CouponDiscountType;
import com.magampick.coupon.domain.CouponKind;
import com.magampick.coupon.domain.CouponStatus;
import com.magampick.coupon.domain.UserCoupon;
import com.magampick.coupon.dto.CouponResponse;
import com.magampick.customer.domain.Customer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.test.util.ReflectionTestUtils;

/** 쿠폰 도메인 테스트 픽스처. */
public class CouponFixture {

  private CouponFixture() {}

  /** 가입 축하 SIGNUP 쿠폰 마스터. */
  public static Coupon aSignupCoupon() {
    Coupon c =
        Coupon.builder()
            .kind(CouponKind.SIGNUP)
            .label("신규 가입 축하 쿠폰")
            .discountType(CouponDiscountType.RATE)
            .discountValue(20)
            .minOrder(5000)
            .validUntil(null)
            .validityDays(30)
            .issueLimit(null)
            .active(true)
            .build();
    ReflectionTestUtils.setField(c, "id", 1L);
    return c;
  }

  /** 이벤트 쿠폰 마스터 (한도 있음). 노출 기간: 2026-06-01 ~ 2026-12-31 — 고정 Clock(2026-06-08) 기준 ONGOING. */
  public static Coupon anEventCoupon() {
    Coupon c =
        Coupon.builder()
            .kind(CouponKind.EVENT)
            .label("봄맞이 이벤트 쿠폰")
            .discountType(CouponDiscountType.AMOUNT)
            .discountValue(3000)
            .minOrder(10000)
            .validUntil(LocalDate.of(2026, 12, 31))
            .validityDays(null)
            .issueLimit(100)
            .active(true)
            .displayStartAt(LocalDate.of(2026, 6, 1))
            .displayEndAt(LocalDate.of(2026, 12, 31))
            .build();
    ReflectionTestUtils.setField(c, "id", 2L);
    return c;
  }

  /** 이벤트 쿠폰 마스터 (한도 없음). 노출 기간: 2026-06-01 ~ 2026-12-31 — 고정 Clock(2026-06-08) 기준 ONGOING. */
  public static Coupon anUnlimitedEventCoupon() {
    Coupon c =
        Coupon.builder()
            .kind(CouponKind.EVENT)
            .label("무제한 이벤트 쿠폰")
            .discountType(CouponDiscountType.RATE)
            .discountValue(10)
            .minOrder(0)
            .validUntil(LocalDate.of(2026, 12, 31))
            .validityDays(null)
            .issueLimit(null)
            .active(true)
            .displayStartAt(LocalDate.of(2026, 6, 1))
            .displayEndAt(LocalDate.of(2026, 12, 31))
            .build();
    ReflectionTestUtils.setField(c, "id", 3L);
    return c;
  }

  /** USABLE UserCoupon 인스턴스 (미만료). discountType/discountValue/minOrder 는 coupon 마스터 값 스냅샷. */
  public static UserCoupon aUsableUserCoupon(Customer customer, Coupon coupon) {
    UserCoupon uc =
        UserCoupon.builder()
            .customer(customer)
            .coupon(coupon)
            .status(CouponStatus.USABLE)
            .expiresAt(LocalDate.now().plusDays(10))
            .issuedAt(LocalDateTime.now().minusDays(1))
            .discountType(coupon.getDiscountType())
            .discountValue(coupon.getDiscountValue())
            .minOrder(coupon.getMinOrder())
            .build();
    ReflectionTestUtils.setField(uc, "id", 10L);
    return uc;
  }

  /** USABLE UserCoupon 인스턴스 (만료일 경과). discountType/discountValue/minOrder 는 coupon 마스터 값 스냅샷. */
  public static UserCoupon anExpiredUserCoupon(Customer customer, Coupon coupon) {
    UserCoupon uc =
        UserCoupon.builder()
            .customer(customer)
            .coupon(coupon)
            .status(CouponStatus.USABLE)
            // 고정 날짜 — CouponServiceTest 의 고정 Clock(2026-06-08) 기준 과거. 실시간 now() 사용 시 날짜 경과로 깨짐
            .expiresAt(LocalDate.of(2026, 6, 7))
            .issuedAt(LocalDateTime.now().minusDays(40))
            .discountType(coupon.getDiscountType())
            .discountValue(coupon.getDiscountValue())
            .minOrder(coupon.getMinOrder())
            .build();
    ReflectionTestUtils.setField(uc, "id", 11L);
    return uc;
  }

  /** CouponResponse 응답 DTO. */
  public static CouponResponse aResponse(Long id, CouponStatus status) {
    return new CouponResponse(
        id, status, CouponDiscountType.RATE, 20, 5000, "신규 가입 축하 쿠폰", LocalDate.now().plusDays(10));
  }
}
