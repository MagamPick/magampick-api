package com.magampick.point.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;

import com.magampick.point.service.PointService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

/** PointConfirmScheduler 단위 테스트. 서비스 위임 여부 + 격리 동작 검증. */
@ExtendWith(MockitoExtension.class)
class PointConfirmSchedulerTest {

  @Mock PointService pointService;

  // @Spy: fixed Clock 구현체를 wrap해 실제 instant()/getZone() 동작을 유지
  @Spy Clock clock = Clock.fixed(Instant.parse("2026-06-13T00:00:00Z"), ZoneId.of("Asia/Seoul"));

  @InjectMocks PointConfirmScheduler scheduler;

  @Test
  void 확정배치_대상있으면_confirm_위임() {
    // given
    given(pointService.findConfirmTargetOrderIds(any())).willReturn(List.of(1L, 2L, 3L));

    // when
    scheduler.confirmPendingAccruals();

    // then: 각 orderId 에 대해 confirm 호출
    then(pointService).should().confirm(1L);
    then(pointService).should().confirm(2L);
    then(pointService).should().confirm(3L);
  }

  @Test
  void 확정배치_대상없으면_confirm_미호출() {
    // given
    given(pointService.findConfirmTargetOrderIds(any())).willReturn(List.of());

    // when
    scheduler.confirmPendingAccruals();

    // then
    then(pointService).should(never()).confirm(any());
  }

  @Test
  void 확정_한건_실패해도_나머지_계속_진행() {
    // given
    given(pointService.findConfirmTargetOrderIds(any())).willReturn(List.of(10L, 20L, 30L));
    doThrow(new RuntimeException("DB 오류")).when(pointService).confirm(10L);

    // when — 예외가 외부로 전파되지 않아야 함
    scheduler.confirmPendingAccruals();

    // then: 20L, 30L 은 여전히 호출됨
    then(pointService).should().confirm(20L);
    then(pointService).should().confirm(30L);
  }
}
