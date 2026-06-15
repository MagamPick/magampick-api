package com.magampick.point.scheduler;

import com.magampick.point.service.PointService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 포인트 적립 확정 배치. completedAt+3일 경과 + 미환불 주문의 PENDING 적립 lot 을 ACTIVE 로 전이. 매시간 정각 실행.
 * app.scheduler.enabled=true 일 때만 활성화.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.scheduler.enabled", havingValue = "true")
public class PointConfirmScheduler {

  private static final int CONFIRM_WINDOW_DAYS = 3;

  private final PointService pointService;
  private final Clock clock;

  /** 매시간 정각 실행. 각 주문을 독립 트랜잭션으로 처리 — 한 건 실패가 나머지에 영향 없음. */
  @Scheduled(cron = "0 0 * * * *")
  public void confirmPendingAccruals() {
    LocalDateTime threshold = LocalDateTime.now(clock).minusDays(CONFIRM_WINDOW_DAYS);
    List<Long> ids = pointService.findConfirmTargetOrderIds(threshold);
    int ok = 0;
    for (Long orderId : ids) {
      try {
        pointService.confirm(orderId);
        ok++;
      } catch (Exception e) {
        log.error("포인트 적립 확정 실패. orderId={}", orderId, e);
      }
    }
    if (!ids.isEmpty()) log.info("포인트 적립 확정 배치 완료. 대상={}, 성공={}", ids.size(), ok);
  }
}
