package com.magampick.refund.service;

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

  /** 매시간 정각 실행. */
  @Scheduled(cron = "0 0 * * * *")
  public void autoApprove() {
    log.info("환불 자동 승인 배치 시작");
    refundService.autoApproveExpiredRefunds();
  }
}
