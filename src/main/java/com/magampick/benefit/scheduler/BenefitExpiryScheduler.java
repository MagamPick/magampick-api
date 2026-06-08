package com.magampick.benefit.scheduler;

import com.magampick.coupon.service.CouponService;
import com.magampick.point.service.PointService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 혜택(포인트·쿠폰) 소멸 배치 스케줄러. 매일 04:00 KST 에 실행. app.scheduler.enabled=true 일 때만 활성화. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.scheduler.enabled", havingValue = "true")
public class BenefitExpiryScheduler {

  private final PointService pointService;
  private final CouponService couponService;

  /** 매일 04:00 KST — 포인트 소멸 + 쿠폰 소멸. 각 배치는 독립 실행 — 한쪽 실패가 다른 쪽을 막지 않음. */
  @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
  public void expireBenefits() {
    try {
      pointService.expireAccruals();
    } catch (Exception e) {
      log.error("포인트 소멸 배치 실패", e);
    }
    try {
      couponService.expireCoupons();
    } catch (Exception e) {
      log.error("쿠폰 소멸 배치 실패", e);
    }
    // TODO(Phase 7): 소멸 예정 알림(포인트 30일 전 / 쿠폰 7일 전) — notification 도메인 구현 후 트리거 연결
  }
}
