package com.magampick.clearance.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.magampick.clearance.service.ClearanceItemService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClearanceItemSchedulerTest {

  @Mock ClearanceItemService clearanceItemService;
  @InjectMocks ClearanceItemScheduler clearanceItemScheduler;

  @Test
  void 자동_마감_처리_대상_있음() {
    // given
    given(clearanceItemService.autoCloseExpiredItems(any(LocalDateTime.class))).willReturn(3);

    // when
    clearanceItemScheduler.autoCloseExpiredItems();

    // then
    then(clearanceItemService).should().autoCloseExpiredItems(any(LocalDateTime.class));
  }

  @Test
  void 자동_마감_처리_대상_없음() {
    // given
    given(clearanceItemService.autoCloseExpiredItems(any(LocalDateTime.class))).willReturn(0);

    // when
    clearanceItemScheduler.autoCloseExpiredItems();

    // then
    then(clearanceItemService).should().autoCloseExpiredItems(any(LocalDateTime.class));
  }
}
