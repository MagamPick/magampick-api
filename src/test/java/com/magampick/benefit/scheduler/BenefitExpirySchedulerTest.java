package com.magampick.benefit.scheduler;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.magampick.coupon.service.CouponService;
import com.magampick.point.service.PointService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** BenefitExpiryScheduler 단위 테스트. 서비스 위임 여부 + 격리 동작 검증. */
@ExtendWith(MockitoExtension.class)
class BenefitExpirySchedulerTest {

  @Mock PointService pointService;
  @Mock CouponService couponService;

  @InjectMocks BenefitExpiryScheduler scheduler;

  @Test
  void 소멸배치_포인트_쿠폰_서비스_위임() {
    // given — 반환값 미사용 (try-catch 내부에서 값 포착 없음); 단순 위임 확인
    // when
    scheduler.expireBenefits();

    // then
    then(pointService).should().expireAccruals();
    then(couponService).should().expireCoupons();
  }

  @Test
  void 포인트소멸_실패해도_쿠폰소멸_실행됨() {
    // given — 포인트 소멸 실패
    given(pointService.expireAccruals()).willThrow(new RuntimeException("포인트 DB 오류"));

    // when — 예외가 외부로 전파되지 않아야 함
    scheduler.expireBenefits();

    // then — 쿠폰 소멸은 여전히 호출됨
    then(couponService).should().expireCoupons();
  }
}
