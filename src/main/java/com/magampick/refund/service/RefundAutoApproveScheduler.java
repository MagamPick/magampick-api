package com.magampick.refund.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 환불 자동 승인 배치. requestedAt + 3일 이후인 REQUESTED 상태 환불을 APPROVED 로 전이. app.scheduler.enabled=true 일 때만
 * 활성화.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.scheduler.enabled", havingValue = "true")
public class RefundAutoApproveScheduler {

  private final RefundService refundService;

  /** 매시간 정각 실행. 각 환불을 독립 트랜잭션으로 처리 — 한 건 실패가 나머지에 영향 없음. */
  @Scheduled(cron = "0 0 * * * *")
  public void autoApprove() {
    List<Long> ids = refundService.findAutoApproveTargetIds();
    int ok = 0;
    for (Long id : ids) {
      try {
        refundService.approveAndReverse(id);
        ok++;
      } catch (Exception e) {
        log.error("환불 자동 승인 실패. refundId={}", id, e);
      }
    }
    if (!ids.isEmpty()) log.info("환불 자동 승인 배치 완료. 대상={}, 성공={}", ids.size(), ok);
  }
}
