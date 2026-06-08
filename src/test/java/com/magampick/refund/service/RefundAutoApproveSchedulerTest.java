package com.magampick.refund.service;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import com.magampick.order.domain.Order;
import com.magampick.refund.domain.Refund;
import com.magampick.refund.fixture.RefundFixture;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** RefundAutoApproveScheduler 단위 테스트. 건별 격리 동작 검증. */
@ExtendWith(MockitoExtension.class)
class RefundAutoApproveSchedulerTest {

  @Mock RefundService refundService;

  @InjectMocks RefundAutoApproveScheduler scheduler;

  @Test
  void 자동승인_대상_건별_호출() {
    // given
    given(refundService.findAutoApproveTargetIds()).willReturn(List.of(1L, 2L, 3L));

    // when
    scheduler.autoApprove();

    // then — 각 id 에 대해 approveAndReverse 호출
    then(refundService).should().approveAndReverse(1L);
    then(refundService).should().approveAndReverse(2L);
    then(refundService).should().approveAndReverse(3L);
  }

  @Test
  void 한건_실패해도_나머지_처리됨() {
    // given — id=1 에서 예외 발생
    given(refundService.findAutoApproveTargetIds()).willReturn(List.of(1L, 2L, 3L));
    willThrow(new RuntimeException("포인트 서비스 오류")).given(refundService).approveAndReverse(1L);

    // when — 예외가 스케줄러 밖으로 전파되지 않아야 함
    assertThatNoException().isThrownBy(() -> scheduler.autoApprove());

    // then — 실패한 id 이후 나머지 id 도 처리됨
    then(refundService).should().approveAndReverse(2L);
    then(refundService).should().approveAndReverse(3L);
  }

  @Test
  void 대상없으면_approveAndReverse_미호출() {
    // given
    given(refundService.findAutoApproveTargetIds()).willReturn(List.of());

    // when
    scheduler.autoApprove();

    // then — approveAndReverse 는 한 번도 호출되지 않음
    then(refundService).should(never()).approveAndReverse(anyLong());
  }

  // ══════════════════════════════════════════════════════════════════════════
  // reminderJob
  // ══════════════════════════════════════════════════════════════════════════

  @Test
  void 리마인드_대상_건별_호출() {
    // given — D+2 경과한 REQUESTED 환불 2건
    Order order = RefundFixture.aCompletedOrder();
    Refund refund1 = RefundFixture.anExpiredRequestedRefund(order);
    Refund refund2 = RefundFixture.anExpiredRequestedRefund(order);
    ReflectionTestUtils.setField(refund2, "id", 3L);

    given(refundService.findReminderTargets()).willReturn(List.of(refund1, refund2));

    // when
    scheduler.reminderJob();

    // then — 각 환불 id 에 대해 sendReminderAndMark 호출
    then(refundService).should().sendReminderAndMark(2L);
    then(refundService).should().sendReminderAndMark(3L);
  }

  @Test
  void 리마인드_한건_실패해도_나머지_처리됨() {
    // given — id=2 에서 예외 발생
    Order order = RefundFixture.aCompletedOrder();
    Refund refund1 = RefundFixture.anExpiredRequestedRefund(order);
    Refund refund2 = RefundFixture.anExpiredRequestedRefund(order);
    ReflectionTestUtils.setField(refund2, "id", 3L);

    given(refundService.findReminderTargets()).willReturn(List.of(refund1, refund2));
    willThrow(new RuntimeException("FCM 오류")).given(refundService).sendReminderAndMark(2L);

    // when — 예외가 스케줄러 밖으로 전파되지 않아야 함
    assertThatNoException().isThrownBy(() -> scheduler.reminderJob());

    // then — 실패한 건 이후 나머지도 처리됨
    then(refundService).should().sendReminderAndMark(3L);
  }

  @Test
  void 리마인드_대상없으면_sendReminderAndMark_미호출() {
    // given
    given(refundService.findReminderTargets()).willReturn(List.of());

    // when
    scheduler.reminderJob();

    // then — sendReminderAndMark 는 한 번도 호출되지 않음
    then(refundService).should(never()).sendReminderAndMark(anyLong());
  }
}
