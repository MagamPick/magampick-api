package com.magampick.settlement.service;

import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 정산 자동 배치 스케줄러. app.scheduler.enabled=true 일 때만 활성화.
 *
 * <ul>
 *   <li>매월 1일 00:10 → 전월 말일 기준(half=2) 정산
 *   <li>매월 16일 00:10 → 당월 15일 기준(half=1) 정산
 * </ul>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.scheduler.enabled", havingValue = "true")
@RequiredArgsConstructor
public class SettlementScheduler {

  private final SettlementService settlementService;
  private final Clock clock;

  /** 직전 반월 기준 정산 처리. 1일 실행 → yesterday=전월 말일(half=2), 16일 실행 → yesterday=당월 15일(half=1). */
  @Scheduled(cron = "0 10 0 1,16 * *")
  public void processSettlement() {
    LocalDate yesterday = LocalDate.now(clock).minusDays(1);
    log.info("정산 배치 시작. 기준날짜={}", yesterday);
    int count = settlementService.processBatch(yesterday);
    log.info("정산 배치 완료. 처리 매장 수={}, 기준날짜={}", count, yesterday);
  }
}
