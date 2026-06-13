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

  @Test
  void isPending_PENDING_상태_true() {
    // given
    PointAccrual pending =
        PointAccrual.builder()
            .customer(null)
            .order(null)
            .initialAmount(500L)
            .remainingAmount(500L)
            .earnedAt(null)
            .expiresAt(null)
            .status(PointAccrualStatus.PENDING)
            .build();

    // then
    assertThat(pending.isPending()).isTrue();
    assertThat(buildAccrual(100L).isPending()).isFalse();
  }

  @Test
  void confirm_PENDING에서_ACTIVE전이_earnedAt_expiresAt_설정() {
    // given
    PointAccrual pending =
        PointAccrual.builder()
            .customer(null)
            .order(null)
            .initialAmount(500L)
            .remainingAmount(500L)
            .earnedAt(null)
            .expiresAt(null)
            .status(PointAccrualStatus.PENDING)
            .build();
    LocalDateTime now = LocalDateTime.of(2026, 6, 13, 12, 0);

    // when
    pending.confirm(now);

    // then
    assertThat(pending.getStatus()).isEqualTo(PointAccrualStatus.ACTIVE);
    assertThat(pending.getEarnedAt()).isEqualTo(now);
    assertThat(pending.getExpiresAt()).isEqualTo(now.plusYears(1));
  }

  @Test
  void confirm_ACTIVE_lot에_호출하면_예외() {
    // given
    PointAccrual active = buildAccrual(200L);

    // when / then
    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalStateException.class, () -> active.confirm(LocalDateTime.now()));
  }
}
