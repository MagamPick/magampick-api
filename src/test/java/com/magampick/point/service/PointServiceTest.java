package com.magampick.point.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.magampick.customer.domain.Customer;
import com.magampick.point.domain.PointReason;
import com.magampick.point.dto.PointHistoryFilter;
import com.magampick.point.dto.PointSummaryResponse;
import com.magampick.point.dto.PointTransactionResponse;
import com.magampick.point.fixture.PointFixture;
import com.magampick.point.mapper.PointTransactionMapper;
import com.magampick.point.repository.PointAccrualRepository;
import com.magampick.point.repository.PointTransactionRepository;
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
  @InjectMocks PointService pointService;

  private static final Long CUSTOMER_ID = 1L;

  private Customer customer() {
    Customer c =
        Customer.builder().email("test@example.com").passwordHash("hash").nickname("테스터").build();
    ReflectionTestUtils.setField(c, "id", CUSTOMER_ID);
    return c;
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
}
