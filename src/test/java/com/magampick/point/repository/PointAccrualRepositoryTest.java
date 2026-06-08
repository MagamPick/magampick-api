package com.magampick.point.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.magampick.TestcontainersConfiguration;
import com.magampick.customer.domain.Customer;
import com.magampick.customer.repository.CustomerRepository;
import com.magampick.global.config.JpaAuditingConfig;
import com.magampick.point.domain.PointAccrual;
import com.magampick.point.domain.PointAccrualStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** PointAccrualRepository 커스텀 쿼리 검증. findByCustomerIdAndStatusOrderByEarnedAtAscIdAsc — FIFO 정렬 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
class PointAccrualRepositoryTest {

  @Autowired PointAccrualRepository pointAccrualRepository;
  @Autowired CustomerRepository customerRepository;

  private Customer customer;

  @BeforeEach
  void setUp() {
    customer =
        customerRepository.save(
            Customer.builder()
                .email("accrual_" + System.nanoTime() + "@test.com")
                .passwordHash("x")
                .nickname("적립테스터")
                .build());
  }

  @Test
  void findByCustomerIdAndStatus_FIFO_정렬() {
    // given: 시간 차이가 있는 ACTIVE lot 3개 (newer 먼저 저장 — id 역순이어도 earnedAt asc 로 정렬돼야 함)
    LocalDateTime oldest = LocalDateTime.now().minusDays(3);
    LocalDateTime middle = LocalDateTime.now().minusDays(2);
    LocalDateTime newest = LocalDateTime.now().minusDays(1);

    saveAccrual(300L, newest);
    saveAccrual(100L, oldest);
    saveAccrual(200L, middle);

    // when
    List<PointAccrual> result =
        pointAccrualRepository.findByCustomerIdAndStatusOrderByEarnedAtAscIdAsc(
            customer.getId(), PointAccrualStatus.ACTIVE);

    // then: FIFO 순 — oldest(100) → middle(200) → newest(300)
    assertThat(result).hasSize(3);
    assertThat(result.get(0).getInitialAmount()).isEqualTo(100L);
    assertThat(result.get(1).getInitialAmount()).isEqualTo(200L);
    assertThat(result.get(2).getInitialAmount()).isEqualTo(300L);
  }

  @Test
  void findByCustomerIdAndStatus_EXHAUSTED_제외() {
    // given: ACTIVE 1개 + EXHAUSTED 1개
    saveAccrual(500L, LocalDateTime.now().minusDays(1));
    pointAccrualRepository.save(
        PointAccrual.builder()
            .customer(customer)
            .order(null)
            .initialAmount(999L)
            .remainingAmount(0L)
            .earnedAt(LocalDateTime.now().minusDays(5))
            .expiresAt(LocalDateTime.now().plusYears(1))
            .status(PointAccrualStatus.EXHAUSTED)
            .build());

    // when
    List<PointAccrual> result =
        pointAccrualRepository.findByCustomerIdAndStatusOrderByEarnedAtAscIdAsc(
            customer.getId(), PointAccrualStatus.ACTIVE);

    // then: ACTIVE 1개만
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getInitialAmount()).isEqualTo(500L);
  }

  // ── helpers ──────────────────────────────────────────────────────────────────

  private void saveAccrual(long amount, LocalDateTime earnedAt) {
    pointAccrualRepository.save(
        PointAccrual.builder()
            .customer(customer)
            .order(null)
            .initialAmount(amount)
            .remainingAmount(amount)
            .earnedAt(earnedAt)
            .expiresAt(earnedAt.plusYears(1))
            .status(PointAccrualStatus.ACTIVE)
            .build());
  }
}
