package com.magampick.clearance.scheduler;

import com.magampick.clearance.service.ClearanceItemService;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClearanceItemScheduler {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private final ClearanceItemService clearanceItemService;

  @Scheduled(cron = "0 */5 * * * *")
  public void autoCloseExpiredItems() {
    int count = clearanceItemService.autoCloseExpiredItems(LocalDateTime.now(KST));
    if (count > 0) {
      log.info("자동 마감 처리됨. count={}", count);
    }
  }
}
