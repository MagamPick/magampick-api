package com.magampick.order.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * AWAITING_PAYMENT 주문 만료 배치. TTL(30분) 경과한 미결제 주문을 CANCELLED 로 전이 + 떨이 재고복원. 10분 주기 실행.
 * app.scheduler.enabled=true 일 때만 활성화.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.scheduler.enabled", havingValue = "true")
public class OrderExpiryScheduler {

  private final OrderService orderService;

  /**
   * 10분마다 실행. 각 주문을 OrderService.cancelExpiredAwaitingOrder 로 위임 (cross-bean → @Transactional 정상
   * 적용).
   */
  @Scheduled(cron = "0 */10 * * * *")
  public void expireAwaitingOrders() {
    List<Long> ids = orderService.findExpiredAwaitingOrderIds();
    int ok = 0;
    for (Long id : ids) {
      try {
        orderService.cancelExpiredAwaitingOrder(id);
        ok++;
      } catch (Exception e) {
        log.error("AWAITING 주문 만료 처리 실패. orderId={}", id, e);
      }
    }
    if (!ids.isEmpty()) log.info("AWAITING 주문 만료 배치 완료. 대상={}, 성공={}", ids.size(), ok);
  }
}
