package com.magampick.order.service;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderExpirySchedulerTest {

  @Mock OrderService orderService;

  OrderExpiryScheduler scheduler;

  @BeforeEach
  void setUp() {
    scheduler = new OrderExpiryScheduler(orderService);
  }

  @Test
  void 만료_주문_있으면_건별_cancelExpiredAwaitingOrder_위임() {
    given(orderService.findExpiredAwaitingOrderIds()).willReturn(List.of(1L, 2L, 3L));

    scheduler.expireAwaitingOrders();

    then(orderService)
        .should(times(3))
        .cancelExpiredAwaitingOrder(org.mockito.ArgumentMatchers.anyLong());
    then(orderService).should().cancelExpiredAwaitingOrder(1L);
    then(orderService).should().cancelExpiredAwaitingOrder(2L);
    then(orderService).should().cancelExpiredAwaitingOrder(3L);
  }

  @Test
  void 만료_주문_없으면_위임_안함() {
    given(orderService.findExpiredAwaitingOrderIds()).willReturn(List.of());

    scheduler.expireAwaitingOrders();

    then(orderService)
        .should(never())
        .cancelExpiredAwaitingOrder(org.mockito.ArgumentMatchers.anyLong());
  }

  @Test
  void 한_건_실패해도_나머지_계속_위임() {
    given(orderService.findExpiredAwaitingOrderIds()).willReturn(List.of(10L, 20L));
    org.mockito.Mockito.doThrow(new RuntimeException("DB 오류"))
        .when(orderService)
        .cancelExpiredAwaitingOrder(10L);

    scheduler.expireAwaitingOrders();

    // 10L 실패 후에도 20L 처리
    then(orderService).should().cancelExpiredAwaitingOrder(20L);
  }
}
