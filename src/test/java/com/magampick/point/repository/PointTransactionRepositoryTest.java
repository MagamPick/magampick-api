package com.magampick.point.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.magampick.TestcontainersConfiguration;
import com.magampick.customer.domain.Customer;
import com.magampick.customer.repository.CustomerRepository;
import com.magampick.global.config.JpaAuditingConfig;
import com.magampick.point.domain.PointAccrual;
import com.magampick.point.domain.PointAccrualStatus;
import com.magampick.point.domain.PointReason;
import com.magampick.point.domain.PointTransaction;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * 포인트 Repository 커스텀 쿼리 검증. sumActiveRemainingByCustomerId — ACTIVE lot 합산 / EXHAUSTED 제외
 * findByCustomerIdAndReasonInOrderByOccurredAtDescIdDesc — 필터 + 최신순 정렬
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
class PointTransactionRepositoryTest {

  @Autowired PointAccrualRepository pointAccrualRepository;
  @Autowired PointTransactionRepository pointTransactionRepository;
  @Autowired CustomerRepository customerRepository;

  private Customer customer;

  @BeforeEach
  void setUp() {
    customer =
        customerRepository.save(
            Customer.builder()
                .email("point_" + System.nanoTime() + "@test.com")
                .passwordHash("x")
                .nickname("포인트테스터")
                .build());
  }

  // ── sumActiveRemainingByCustomerId ────────────────────────────────────────────

  @Test
  void 잔액_합산_ACTIVE_lot_만_포함() {
    // given: ACTIVE 2000 + EXHAUSTED 500
    saveAccrual(2000L, 2000L, PointAccrualStatus.ACTIVE);
    saveAccrual(500L, 0L, PointAccrualStatus.EXHAUSTED);

    // when
    long balance = pointAccrualRepository.sumActiveRemainingByCustomerId(customer.getId());

    // then: ACTIVE lot 잔량만 합산
    assertThat(balance).isEqualTo(2000L);
  }

  @Test
  void 잔액_합산_여러_ACTIVE_lot_합계() {
    // given: ACTIVE 1000 + ACTIVE 500
    saveAccrual(1000L, 800L, PointAccrualStatus.ACTIVE);
    saveAccrual(500L, 300L, PointAccrualStatus.ACTIVE);

    // when
    long balance = pointAccrualRepository.sumActiveRemainingByCustomerId(customer.getId());

    // then: 800 + 300 = 1100
    assertThat(balance).isEqualTo(1100L);
  }

  @Test
  void 잔액_합산_포인트_없으면_0() {
    // given: 아무것도 없음

    // when
    long balance = pointAccrualRepository.sumActiveRemainingByCustomerId(customer.getId());

    // then
    assertThat(balance).isZero();
  }

  @Test
  void 잔액_합산_다른_소비자_포인트_제외() {
    // given: 다른 소비자의 ACTIVE lot
    Customer other =
        customerRepository.save(
            Customer.builder()
                .email("other_" + System.nanoTime() + "@test.com")
                .passwordHash("x")
                .nickname("다른소비자")
                .build());
    pointAccrualRepository.save(
        PointAccrual.builder()
            .customer(other)
            .order(null)
            .initialAmount(5000L)
            .remainingAmount(5000L)
            .earnedAt(LocalDateTime.now())
            .expiresAt(LocalDateTime.now().plusYears(1))
            .status(PointAccrualStatus.ACTIVE)
            .build());

    // when: customer 의 잔액 조회
    long balance = pointAccrualRepository.sumActiveRemainingByCustomerId(customer.getId());

    // then: 내 것은 없음
    assertThat(balance).isZero();
  }

  // ── findByCustomerIdAndReasonInOrderByOccurredAtDescIdDesc ───────────────────

  @Test
  void 내역_필터_EARN_사유만_반환() {
    // given: EARN 1건 + USE 1건
    saveTransaction(PointReason.EARN, 1000L, LocalDateTime.now().minusHours(1));
    saveTransaction(PointReason.USE, 500L, LocalDateTime.now());

    // when
    List<PointTransaction> result =
        pointTransactionRepository.findByCustomerIdAndReasonInOrderByOccurredAtDescIdDesc(
            customer.getId(), List.of(PointReason.EARN));

    // then: EARN 만 반환
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getReason()).isEqualTo(PointReason.EARN);
  }

  @Test
  void 내역_필터_USE_EXPIRE_사유_반환() {
    // given: EARN + USE + EXPIRE
    saveTransaction(PointReason.EARN, 1000L, LocalDateTime.now().minusHours(3));
    saveTransaction(PointReason.USE, 500L, LocalDateTime.now().minusHours(2));
    saveTransaction(PointReason.EXPIRE, 200L, LocalDateTime.now().minusHours(1));

    // when
    List<PointTransaction> result =
        pointTransactionRepository.findByCustomerIdAndReasonInOrderByOccurredAtDescIdDesc(
            customer.getId(), List.of(PointReason.USE, PointReason.EXPIRE));

    // then: USE + EXPIRE 만 반환
    assertThat(result).hasSize(2);
    assertThat(result)
        .extracting(PointTransaction::getReason)
        .containsExactlyInAnyOrder(PointReason.USE, PointReason.EXPIRE);
  }

  @Test
  void 내역_최신순_정렬() {
    // given: 시간 차이가 있는 EARN 3건
    LocalDateTime older = LocalDateTime.now().minusDays(2);
    LocalDateTime middle = LocalDateTime.now().minusDays(1);
    LocalDateTime newer = LocalDateTime.now();

    saveTransaction(PointReason.EARN, 100L, older);
    saveTransaction(PointReason.EARN, 200L, middle);
    saveTransaction(PointReason.EARN, 300L, newer);

    // when
    List<PointTransaction> result =
        pointTransactionRepository.findByCustomerIdAndReasonInOrderByOccurredAtDescIdDesc(
            customer.getId(), List.of(PointReason.EARN));

    // then: 최신(300) → 중간(200) → 오래된(100) 순
    assertThat(result).hasSize(3);
    assertThat(result.get(0).getAmount()).isEqualTo(300L);
    assertThat(result.get(1).getAmount()).isEqualTo(200L);
    assertThat(result.get(2).getAmount()).isEqualTo(100L);
  }

  @Test
  void 내역_다른_소비자_포함_안됨() {
    // given: 다른 소비자의 EARN
    Customer other =
        customerRepository.save(
            Customer.builder()
                .email("other2_" + System.nanoTime() + "@test.com")
                .passwordHash("x")
                .nickname("다른소비자2")
                .build());
    pointTransactionRepository.save(
        PointTransaction.builder()
            .customer(other)
            .order(null)
            .reason(PointReason.EARN)
            .amount(9999L)
            .storeName(null)
            .occurredAt(LocalDateTime.now())
            .build());

    // when: customer 의 내역 조회
    List<PointTransaction> result =
        pointTransactionRepository.findByCustomerIdAndReasonInOrderByOccurredAtDescIdDesc(
            customer.getId(), List.of(PointReason.EARN));

    // then: 내 것은 없음
    assertThat(result).isEmpty();
  }

  // ── helpers ──────────────────────────────────────────────────────────────────

  private void saveAccrual(long initial, long remaining, PointAccrualStatus status) {
    pointAccrualRepository.save(
        PointAccrual.builder()
            .customer(customer)
            .order(null)
            .initialAmount(initial)
            .remainingAmount(remaining)
            .earnedAt(LocalDateTime.now())
            .expiresAt(LocalDateTime.now().plusYears(1))
            .status(status)
            .build());
  }

  private void saveTransaction(PointReason reason, long amount, LocalDateTime occurredAt) {
    pointTransactionRepository.save(
        PointTransaction.builder()
            .customer(customer)
            .order(null)
            .reason(reason)
            .amount(amount)
            .storeName(null)
            .occurredAt(occurredAt)
            .build());
  }
}
