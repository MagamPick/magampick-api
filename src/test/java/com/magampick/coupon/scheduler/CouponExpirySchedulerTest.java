package com.magampick.coupon.scheduler;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.magampick.coupon.service.CouponService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** CouponExpiryScheduler 단위 테스트. 서비스 위임 여부 + 격리 동작 검증. */
@ExtendWith(MockitoExtension.class)
class CouponExpirySchedulerTest {

  @Mock CouponService couponService;

  @InjectMocks CouponExpiryScheduler scheduler;

  @Test
  void 쿠폰소멸배치_서비스_위임() {
    // when
    scheduler.expireAndNotify();

    // then
    then(couponService).should().expireCoupons();
    then(couponService).should().notifyExpiringCoupons();
  }

  @Test
  void 쿠폰소멸_실패해도_알림배치_실행됨() {
    // given — 쿠폰 소멸 실패 (expireCoupons returns int — non-void stub form)
    given(couponService.expireCoupons()).willThrow(new RuntimeException("쿠폰 DB 오류"));

    // when — 예외가 외부로 전파되지 않아야 함
    scheduler.expireAndNotify();

    // then — 알림 배치는 여전히 호출됨
    then(couponService).should().notifyExpiringCoupons();
  }
}
