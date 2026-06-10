package com.magampick.point.scheduler;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.magampick.point.service.PointService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** PointExpiryScheduler 단위 테스트. 서비스 위임 여부 + 격리 동작 검증. */
@ExtendWith(MockitoExtension.class)
class PointExpirySchedulerTest {

  @Mock PointService pointService;

  @InjectMocks PointExpiryScheduler scheduler;

  @Test
  void 포인트소멸배치_서비스_위임() {
    // when
    scheduler.expireAndNotify();

    // then
    then(pointService).should().expireAccruals();
    then(pointService).should().notifyExpiringAccruals();
  }

  @Test
  void 포인트소멸_실패해도_알림배치_실행됨() {
    // given — 포인트 소멸 실패
    given(pointService.expireAccruals()).willThrow(new RuntimeException("포인트 DB 오류"));

    // when — 예외가 외부로 전파되지 않아야 함
    scheduler.expireAndNotify();

    // then — 알림 배치는 여전히 호출됨
    then(pointService).should().notifyExpiringAccruals();
  }
}
