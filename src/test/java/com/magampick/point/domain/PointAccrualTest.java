package com.magampick.point.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** PointAccrual 도메인 메서드 단위 테스트. */
class PointAccrualTest {

  private PointAccrual buildAccrual(long initialAmount) {
    LocalDateTime now = LocalDateTime.now();
    return PointAccrual.builder()
        .customer(null)
        .order(null)
        .initialAmount(initialAmount)
        .remainingAmount(initialAmount)
        .earnedAt(now)
        .expiresAt(now.plusYears(1))
        .status(PointAccrualStatus.ACTIVE)
        .build();
  }

  @Test
  void deduct_일부_차감_ACTIVE_유지() {
    // given
    PointAccrual accrual = buildAccrual(500L);

    // when
    accrual.deduct(200L);

    // then
    assertThat(accrual.getRemainingAmount()).isEqualTo(300L);
    assertThat(accrual.getStatus()).isEqualTo(PointAccrualStatus.ACTIVE);
  }

  @Test
  void deduct_전액_차감_EXHAUSTED_전이() {
    // given
    PointAccrual accrual = buildAccrual(300L);

    // when
    accrual.deduct(300L);

    // then
    assertThat(accrual.getRemainingAmount()).isEqualTo(0L);
    assertThat(accrual.getStatus()).isEqualTo(PointAccrualStatus.EXHAUSTED);
  }
}
