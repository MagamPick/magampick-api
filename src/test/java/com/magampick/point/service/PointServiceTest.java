package com.magampick.point.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.magampick.customer.domain.Customer;
import com.magampick.global.exception.BusinessException;
import com.magampick.order.domain.Order;
import com.magampick.point.domain.PointAccrual;
import com.magampick.point.domain.PointAccrualStatus;
import com.magampick.point.domain.PointReason;
import com.magampick.point.domain.PointTransaction;
import com.magampick.point.dto.PointHistoryFilter;
import com.magampick.point.dto.PointSummaryResponse;
import com.magampick.point.dto.PointTransactionResponse;
import com.magampick.point.exception.PointErrorCode;
import com.magampick.point.fixture.PointFixture;
import com.magampick.point.mapper.PointTransactionMapper;
import com.magampick.point.repository.PointAccrualRepository;
import com.magampick.point.repository.PointTransactionRepository;
import com.magampick.store.domain.Store;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PointServiceTest {

  @Mock PointAccrualRepository pointAccrualRepository;
  @Mock PointTransactionRepository pointTransactionRepository;
  @Mock PointTransactionMapper pointTransactionMapper;

  // 2026-06-08 KST 고정 Clock
  private final Clock fixedClock =
      Clock.fixed(Instant.parse("2026-06-08T00:00:00Z"), ZoneId.of("Asia/Seoul"));

  @InjectMocks PointService pointService;

  private static final Long CUSTOMER_ID = 1L;

  private Customer customer() {
    Customer c =
        Customer.builder().email("test@example.com").passwordHash("hash").nickname("테스터").build();
    ReflectionTestUtils.setField(c, "id", CUSTOMER_ID);
    return c;
  }

  private Order mockOrder(Customer customer) {
    Order order = mock(Order.class);
    Store store = mock(Store.class);
    given(order.getCustomer()).willReturn(customer);
    // store/id 는 성공 경로에서만 사용 — 예외 경로에서 unused stub 경고 방지
    lenient().when(order.getStore()).thenReturn(store);
    lenient().when(store.getName()).thenReturn("동네빵집");
    lenient().when(order.getId()).thenReturn(42L);
    return order;
  }

  private void injectClock() {
    ReflectionTestUtils.setField(pointService, "clock", fixedClock);
  }

  // ── 잔액 조회 ────────────────────────────────────────────────────────────────

  @Test
  void 잔액_조회_성공() {
    // given
    given(pointAccrualRepository.sumActiveRemainingByCustomerId(CUSTOMER_ID)).willReturn(3000L);

    // when
    PointSummaryResponse response = pointService.getSummary(CUSTOMER_ID);

    // then
    assertThat(response.balance()).isEqualTo(3000L);
  }

  @Test
  void 잔액_조회_포인트_없으면_0() {
    // given
    given(pointAccrualRepository.sumActiveRemainingByCustomerId(CUSTOMER_ID)).willReturn(0L);

    // when
    PointSummaryResponse response = pointService.getSummary(CUSTOMER_ID);

    // then
    assertThat(response.balance()).isZero();
  }

  // ── 내역 조회 ────────────────────────────────────────────────────────────────

  @Test
  void 내역_전체_조회() {
    // given
    Customer customer = customer();
    var txs =
        List.of(
            PointFixture.anEarnTransaction(customer),
            PointFixture.aUseTransaction(customer),
            PointFixture.anExpireTransaction(customer));
    var expected =
        List.of(
            PointFixture.aTransactionResponse(10L, PointReason.EARN),
            PointFixture.aTransactionResponse(11L, PointReason.USE),
            PointFixture.aTransactionResponse(12L, PointReason.EXPIRE));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<java.util.Collection<PointReason>> reasonCaptor =
        ArgumentCaptor.forClass(java.util.Collection.class);

    given(
            pointTransactionRepository.findByCustomerIdAndReasonInOrderByOccurredAtDescIdDesc(
                eq(CUSTOMER_ID), reasonCaptor.capture()))
        .willReturn(txs);
    given(pointTransactionMapper.toResponseList(txs)).willReturn(expected);

    // when
    List<PointTransactionResponse> result =
        pointService.getHistory(CUSTOMER_ID, PointHistoryFilter.ALL);

    // then
    assertThat(result).hasSize(3);
    assertThat(reasonCaptor.getValue())
        .containsExactlyInAnyOrder(
            PointReason.EARN,
            PointReason.USE,
            PointReason.EXPIRE,
            PointReason.RESTORE,
            PointReason.CLAWBACK);
    then(pointTransactionMapper).should().toResponseList(txs);
  }

  @Test
  void 내역_적립_필터() {
    // given
    Customer customer = customer();
    var txs = List.of(PointFixture.anEarnTransaction(customer));
    var expected = List.of(PointFixture.aTransactionResponse(10L, PointReason.EARN));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<java.util.Collection<PointReason>> reasonCaptor =
        ArgumentCaptor.forClass(java.util.Collection.class);

    given(
            pointTransactionRepository.findByCustomerIdAndReasonInOrderByOccurredAtDescIdDesc(
                eq(CUSTOMER_ID), reasonCaptor.capture()))
        .willReturn(txs);
    given(pointTransactionMapper.toResponseList(txs)).willReturn(expected);

    // when
    List<PointTransactionResponse> result =
        pointService.getHistory(CUSTOMER_ID, PointHistoryFilter.EARN);

    // then
    assertThat(result).hasSize(1);
    assertThat(reasonCaptor.getValue())
        .containsExactlyInAnyOrder(PointReason.EARN, PointReason.RESTORE);
  }

  @Test
  void 내역_사용_필터() {
    // given
    Customer customer = customer();
    var txs =
        List.of(PointFixture.aUseTransaction(customer), PointFixture.anExpireTransaction(customer));
    var expected =
        List.of(
            PointFixture.aTransactionResponse(11L, PointReason.USE),
            PointFixture.aTransactionResponse(12L, PointReason.EXPIRE));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<java.util.Collection<PointReason>> reasonCaptor =
        ArgumentCaptor.forClass(java.util.Collection.class);

    given(
            pointTransactionRepository.findByCustomerIdAndReasonInOrderByOccurredAtDescIdDesc(
                eq(CUSTOMER_ID), reasonCaptor.capture()))
        .willReturn(txs);
    given(pointTransactionMapper.toResponseList(txs)).willReturn(expected);

    // when
    List<PointTransactionResponse> result =
        pointService.getHistory(CUSTOMER_ID, PointHistoryFilter.USE);

    // then
    assertThat(result).hasSize(2);
    assertThat(reasonCaptor.getValue())
        .containsExactlyInAnyOrder(PointReason.USE, PointReason.EXPIRE, PointReason.CLAWBACK);
  }

  // ── 적립 ─────────────────────────────────────────────────────────────────────

  @Test
  void 적립_성공_lot과_내역_생성() {
    // given
    injectClock();
    Customer customer = customer();
    Order order = mockOrder(customer);
    given(pointAccrualRepository.save(any(PointAccrual.class)))
        .willAnswer(inv -> inv.getArgument(0));
    given(pointTransactionRepository.save(any(PointTransaction.class)))
        .willAnswer(inv -> inv.getArgument(0));

    // when
    pointService.earn(order, 500L);

    // then: PointAccrual 저장 확인
    @SuppressWarnings("unchecked")
    ArgumentCaptor<PointAccrual> accrualCaptor = ArgumentCaptor.forClass(PointAccrual.class);
    then(pointAccrualRepository).should().save(accrualCaptor.capture());
    PointAccrual savedAccrual = accrualCaptor.getValue();
    assertThat(savedAccrual.getInitialAmount()).isEqualTo(500L);
    assertThat(savedAccrual.getRemainingAmount()).isEqualTo(500L);
    assertThat(savedAccrual.getStatus()).isEqualTo(PointAccrualStatus.ACTIVE);

    // then: PointTransaction 저장 확인
    @SuppressWarnings("unchecked")
    ArgumentCaptor<PointTransaction> txCaptor = ArgumentCaptor.forClass(PointTransaction.class);
    then(pointTransactionRepository).should().save(txCaptor.capture());
    PointTransaction savedTx = txCaptor.getValue();
    assertThat(savedTx.getReason()).isEqualTo(PointReason.EARN);
    assertThat(savedTx.getAmount()).isEqualTo(500L);
  }

  @Test
  void 적립_amount_0이하_무시() {
    // when
    pointService.earn(mock(Order.class), 0L);
    pointService.earn(mock(Order.class), -1L);

    // then: 저장 없음
    then(pointAccrualRepository).should(never()).save(any());
    then(pointTransactionRepository).should(never()).save(any());
  }

  // ── 사용 ─────────────────────────────────────────────────────────────────────

  @Test
  void 사용_FIFO_오래된_lot부터_차감() {
    // given
    injectClock();
    Customer customer = customer();
    Order order = mockOrder(customer);

    // lot 2개: 오래된 것(remainingAmount=300), 새 것(remainingAmount=500)
    LocalDateTime now = LocalDateTime.now(fixedClock);
    PointAccrual oldLot = buildAccrual(customer, 300L, now.minusDays(10));
    PointAccrual newLot = buildAccrual(customer, 500L, now.minusDays(1));

    given(
            pointAccrualRepository.findByCustomerIdAndStatusOrderByEarnedAtAscIdAsc(
                CUSTOMER_ID, PointAccrualStatus.ACTIVE))
        .willReturn(List.of(oldLot, newLot));
    given(pointAccrualRepository.saveAll(any())).willAnswer(inv -> inv.getArgument(0));
    given(pointTransactionRepository.save(any(PointTransaction.class)))
        .willAnswer(inv -> inv.getArgument(0));

    // when: 200 사용 → 오래된 lot 에서만 차감
    pointService.use(order, 200L);

    // then: 오래된 lot 300 → 100 (ACTIVE 유지), 새 lot 500 그대로
    assertThat(oldLot.getRemainingAmount()).isEqualTo(100L);
    assertThat(oldLot.getStatus()).isEqualTo(PointAccrualStatus.ACTIVE);
    assertThat(newLot.getRemainingAmount()).isEqualTo(500L);
  }

  @Test
  void 사용_여러_lot_걸쳐_차감() {
    // given
    injectClock();
    Customer customer = customer();
    Order order = mockOrder(customer);

    LocalDateTime now = LocalDateTime.now(fixedClock);
    PointAccrual lot1 = buildAccrual(customer, 200L, now.minusDays(5));
    PointAccrual lot2 = buildAccrual(customer, 300L, now.minusDays(3));

    given(
            pointAccrualRepository.findByCustomerIdAndStatusOrderByEarnedAtAscIdAsc(
                CUSTOMER_ID, PointAccrualStatus.ACTIVE))
        .willReturn(List.of(lot1, lot2));
    given(pointAccrualRepository.saveAll(any())).willAnswer(inv -> inv.getArgument(0));
    given(pointTransactionRepository.save(any(PointTransaction.class)))
        .willAnswer(inv -> inv.getArgument(0));

    // when: 350 사용 → lot1 완전소진(200) + lot2 에서 150 차감
    pointService.use(order, 350L);

    // then
    assertThat(lot1.getRemainingAmount()).isEqualTo(0L);
    assertThat(lot1.getStatus()).isEqualTo(PointAccrualStatus.EXHAUSTED);
    assertThat(lot2.getRemainingAmount()).isEqualTo(150L);

    // USE 내역 저장 확인
    @SuppressWarnings("unchecked")
    ArgumentCaptor<PointTransaction> txCaptor = ArgumentCaptor.forClass(PointTransaction.class);
    then(pointTransactionRepository).should().save(txCaptor.capture());
    assertThat(txCaptor.getValue().getReason()).isEqualTo(PointReason.USE);
    assertThat(txCaptor.getValue().getAmount()).isEqualTo(350L);
  }

  @Test
  void 사용_잔액부족_예외() {
    // given
    injectClock();
    Customer customer = customer();
    Order order = mockOrder(customer);

    LocalDateTime now = LocalDateTime.now(fixedClock);
    PointAccrual lot = buildAccrual(customer, 100L, now.minusDays(1));

    given(
            pointAccrualRepository.findByCustomerIdAndStatusOrderByEarnedAtAscIdAsc(
                CUSTOMER_ID, PointAccrualStatus.ACTIVE))
        .willReturn(List.of(lot));

    // when / then
    assertThatThrownBy(() -> pointService.use(order, 500L))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", PointErrorCode.INSUFFICIENT_POINTS);

    then(pointTransactionRepository).should(never()).save(any());
  }

  // ── 복원 ─────────────────────────────────────────────────────────────────────

  @Test
  void 복원_새_lot_생성() {
    // given
    injectClock();
    Customer customer = customer();
    Order order = mockOrder(customer);
    given(pointAccrualRepository.save(any(PointAccrual.class)))
        .willAnswer(inv -> inv.getArgument(0));
    given(pointTransactionRepository.save(any(PointTransaction.class)))
        .willAnswer(inv -> inv.getArgument(0));

    // when
    pointService.restore(order, 300L);

    // then: 새 ACTIVE lot 저장
    @SuppressWarnings("unchecked")
    ArgumentCaptor<PointAccrual> accrualCaptor = ArgumentCaptor.forClass(PointAccrual.class);
    then(pointAccrualRepository).should().save(accrualCaptor.capture());
    PointAccrual saved = accrualCaptor.getValue();
    assertThat(saved.getInitialAmount()).isEqualTo(300L);
    assertThat(saved.getRemainingAmount()).isEqualTo(300L);
    assertThat(saved.getStatus()).isEqualTo(PointAccrualStatus.ACTIVE);

    // RESTORE 내역 저장 확인
    @SuppressWarnings("unchecked")
    ArgumentCaptor<PointTransaction> txCaptor = ArgumentCaptor.forClass(PointTransaction.class);
    then(pointTransactionRepository).should().save(txCaptor.capture());
    assertThat(txCaptor.getValue().getReason()).isEqualTo(PointReason.RESTORE);
    assertThat(txCaptor.getValue().getAmount()).isEqualTo(300L);
  }

  // ── 회수(clawback) ──────────────────────────────────────────────────────────

  @Test
  void 회수_미사용분_전액_CLAWBACK() {
    // given
    injectClock();
    Customer customer = customer();
    Order order = mockOrder(customer);

    // EARN lot: remainingAmount=1000 (미사용 전액)
    LocalDateTime now = LocalDateTime.now(fixedClock);
    PointAccrual earnLot = buildAccrual(customer, 1000L, now.minusDays(1));
    given(pointAccrualRepository.findByOrderId(42L)).willReturn(List.of(earnLot));
    given(pointAccrualRepository.saveAll(any())).willAnswer(inv -> inv.getArgument(0));
    given(pointTransactionRepository.save(any(PointTransaction.class)))
        .willAnswer(inv -> inv.getArgument(0));

    // when
    pointService.clawback(order);

    // then: lot 전액 회수 → EXHAUSTED
    assertThat(earnLot.getRemainingAmount()).isEqualTo(0L);
    assertThat(earnLot.getStatus()).isEqualTo(PointAccrualStatus.EXHAUSTED);

    // CLAWBACK 내역 저장 확인
    @SuppressWarnings("unchecked")
    ArgumentCaptor<PointTransaction> txCaptor = ArgumentCaptor.forClass(PointTransaction.class);
    then(pointTransactionRepository).should().save(txCaptor.capture());
    assertThat(txCaptor.getValue().getReason()).isEqualTo(PointReason.CLAWBACK);
    assertThat(txCaptor.getValue().getAmount()).isEqualTo(1000L);
  }

  @Test
  void 회수_일부사용분만() {
    // given: 초기 1000P 중 700 사용 → remainingAmount=300
    injectClock();
    Customer customer = customer();
    Order order = mockOrder(customer);

    LocalDateTime now = LocalDateTime.now(fixedClock);
    PointAccrual earnLot =
        PointAccrual.builder()
            .customer(customer)
            .order(null)
            .initialAmount(1000L)
            .remainingAmount(300L) // 700 사용 후 300 남음
            .earnedAt(now.minusDays(1))
            .expiresAt(now.plusYears(1))
            .status(PointAccrualStatus.ACTIVE)
            .build();

    given(pointAccrualRepository.findByOrderId(42L)).willReturn(List.of(earnLot));
    given(pointAccrualRepository.saveAll(any())).willAnswer(inv -> inv.getArgument(0));
    given(pointTransactionRepository.save(any(PointTransaction.class)))
        .willAnswer(inv -> inv.getArgument(0));

    // when
    pointService.clawback(order);

    // then: 남은 300 만 회수
    assertThat(earnLot.getRemainingAmount()).isEqualTo(0L);
    assertThat(earnLot.getStatus()).isEqualTo(PointAccrualStatus.EXHAUSTED);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<PointTransaction> txCaptor = ArgumentCaptor.forClass(PointTransaction.class);
    then(pointTransactionRepository).should().save(txCaptor.capture());
    assertThat(txCaptor.getValue().getAmount()).isEqualTo(300L);
  }

  @Test
  void 회수_전액사용시_회수0_내역없음() {
    // given: remainingAmount=0 (이미 전부 사용됨) — clock/customer 불필요(트랜잭션 없음)
    Order order = mock(Order.class);
    lenient().when(order.getId()).thenReturn(42L);

    LocalDateTime now = LocalDateTime.now(fixedClock);
    PointAccrual exhaustedLot =
        PointAccrual.builder()
            .customer(customer())
            .order(null)
            .initialAmount(1000L)
            .remainingAmount(0L)
            .earnedAt(now.minusDays(1))
            .expiresAt(now.plusYears(1))
            .status(PointAccrualStatus.EXHAUSTED)
            .build();

    given(pointAccrualRepository.findByOrderId(42L)).willReturn(List.of(exhaustedLot));

    // when
    pointService.clawback(order);

    // then: 회수 0 → 내역 없음
    then(pointTransactionRepository).should(never()).save(any());
    then(pointAccrualRepository).should(never()).saveAll(any());
  }

  // ── 소멸 배치(expireAccruals) ─────────────────────────────────────────────────

  @Test
  void 소멸배치_만료ACTIVE_EXPIRED전이_EXPIRE내역() {
    // given
    injectClock();
    Customer customer = customer();
    LocalDateTime now = LocalDateTime.now(fixedClock);

    // 만료된 ACTIVE lot (remainingAmount=500)
    PointAccrual expiredLot =
        PointAccrual.builder()
            .customer(customer)
            .order(null)
            .initialAmount(500L)
            .remainingAmount(500L)
            .earnedAt(now.minusYears(2))
            .expiresAt(now.minusDays(1)) // 이미 만료
            .status(PointAccrualStatus.ACTIVE)
            .build();

    given(pointAccrualRepository.findByStatusAndExpiresAtBefore(PointAccrualStatus.ACTIVE, now))
        .willReturn(List.of(expiredLot));
    given(pointAccrualRepository.saveAll(any())).willAnswer(inv -> inv.getArgument(0));
    given(pointTransactionRepository.save(any(PointTransaction.class)))
        .willAnswer(inv -> inv.getArgument(0));

    // when
    int count = pointService.expireAccruals();

    // then: lot EXPIRED 전이
    assertThat(expiredLot.getStatus()).isEqualTo(PointAccrualStatus.EXPIRED);
    assertThat(expiredLot.getRemainingAmount()).isEqualTo(0L);
    assertThat(count).isEqualTo(1);

    // EXPIRE 내역 저장 확인
    @SuppressWarnings("unchecked")
    ArgumentCaptor<PointTransaction> txCaptor = ArgumentCaptor.forClass(PointTransaction.class);
    then(pointTransactionRepository).should().save(txCaptor.capture());
    assertThat(txCaptor.getValue().getReason()).isEqualTo(PointReason.EXPIRE);
    assertThat(txCaptor.getValue().getAmount()).isEqualTo(500L);
  }

  // ── helpers ──────────────────────────────────────────────────────────────────

  private PointAccrual buildAccrual(Customer customer, long amount, LocalDateTime earnedAt) {
    return PointAccrual.builder()
        .customer(customer)
        .order(null)
        .initialAmount(amount)
        .remainingAmount(amount)
        .earnedAt(earnedAt)
        .expiresAt(earnedAt.plusYears(1))
        .status(PointAccrualStatus.ACTIVE)
        .build();
  }
}
