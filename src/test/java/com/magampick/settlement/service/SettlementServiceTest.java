package com.magampick.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

import com.magampick.global.exception.BusinessException;
import com.magampick.settlement.domain.Settlement;
import com.magampick.settlement.domain.SettlementStatus;
import com.magampick.settlement.dto.SettlementCycleResponse;
import com.magampick.settlement.dto.SettlementSummaryResponse;
import com.magampick.settlement.fixture.SettlementFixture;
import com.magampick.settlement.mapper.SettlementMapper;
import com.magampick.settlement.repository.SettlementRepository;
import com.magampick.store.domain.Store;
import com.magampick.store.exception.StoreErrorCode;
import com.magampick.store.repository.StoreRepository;
import com.magampick.store.service.StoreService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SettlementServiceTest {

  @Mock SettlementRepository settlementRepository;
  @Mock StoreRepository storeRepository;
  @Mock StoreService storeService;
  @Mock SettlementMapper settlementMapper;
  @Mock Clock clock;

  @InjectMocks SettlementService settlementService;

  private Store store;

  /**
   * Clock 을 2026-06-20 로 고정 — depositDate(6/25) > today → SCHEDULED 케이스. lenient: clock 미사용 테스트에서
   * strict 위반 방지.
   */
  @BeforeEach
  void setUpClock() {
    Instant fixed = Instant.parse("2026-06-20T00:00:00Z");
    lenient().when(clock.instant()).thenReturn(fixed);
    lenient().when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
    store = SettlementFixture.aStore();
  }

  // ── processBatch ──────────────────────────────────────────────────────────────

  @Test
  void 정산_처리_성공_단일매장() {
    // given: 2026-06-10 → half=1 (6/1~6/15)
    LocalDate targetDate = LocalDate.of(2026, 6, 10);
    Object[] row = new Object[] {10L, new BigDecimal("1000000")};
    given(settlementRepository.findGrossAmountByPeriodRaw(any(), any()))
        .willReturn(Collections.singletonList(row));
    given(settlementRepository.existsByStoreIdAndYearAndMonthAndHalf(10L, 2026, 6, 1))
        .willReturn(false);
    given(storeRepository.getReferenceById(10L)).willReturn(store);

    ArgumentCaptor<Settlement> captor = ArgumentCaptor.forClass(Settlement.class);
    given(settlementRepository.save(captor.capture())).willAnswer(inv -> inv.getArgument(0));

    // when
    int count = settlementService.processBatch(targetDate);

    // then
    assertThat(count).isEqualTo(1);
    Settlement saved = captor.getValue();
    assertThat(saved.getYear()).isEqualTo(2026);
    assertThat(saved.getMonth()).isEqualTo(6);
    assertThat(saved.getHalf()).isEqualTo(1);
    assertThat(saved.getGrossAmount()).isEqualByComparingTo("1000000");
    assertThat(saved.getFeeAmount()).isEqualByComparingTo("65000");
    assertThat(saved.getNetAmount()).isEqualByComparingTo("935000");
    assertThat(saved.getStatus()).isEqualTo(SettlementStatus.SCHEDULED);
    assertThat(saved.getPeriodStart()).isEqualTo(LocalDate.of(2026, 6, 1));
    assertThat(saved.getPeriodEnd()).isEqualTo(LocalDate.of(2026, 6, 15));
    assertThat(saved.getDepositDate()).isEqualTo(LocalDate.of(2026, 6, 25));
  }

  @Test
  void 정산_처리_환불승인건_제외() {
    // given: repository 가 이미 환불 제외된 순 금액을 반환한다고 가정
    //        (LEFT JOIN + WHERE r.id IS NULL 는 DB 레이어 — 서비스는 반환값 그대로 사용)
    LocalDate targetDate = LocalDate.of(2026, 6, 10);
    // 환불 2만원 제외된 순 매출 8만원
    Object[] row = new Object[] {10L, new BigDecimal("80000")};
    given(settlementRepository.findGrossAmountByPeriodRaw(any(), any()))
        .willReturn(Collections.singletonList(row));
    given(settlementRepository.existsByStoreIdAndYearAndMonthAndHalf(10L, 2026, 6, 1))
        .willReturn(false);
    given(storeRepository.getReferenceById(10L)).willReturn(store);
    given(settlementRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

    // when
    int count = settlementService.processBatch(targetDate);

    // then: 반환된 금액을 그대로 사용해 수수료 계산
    assertThat(count).isEqualTo(1);
    ArgumentCaptor<Settlement> captor = ArgumentCaptor.forClass(Settlement.class);
    then(settlementRepository).should().save(captor.capture());
    Settlement saved = captor.getValue();
    assertThat(saved.getGrossAmount()).isEqualByComparingTo("80000");
    // 80000 * 0.065 = 5200.0 → setScale(0, DOWN) = 5200
    assertThat(saved.getFeeAmount()).isEqualByComparingTo("5200");
    assertThat(saved.getNetAmount()).isEqualByComparingTo("74800");
  }

  @Test
  void 정산_처리_중복실행_스킵() {
    // given: 이미 같은 회차 존재
    LocalDate targetDate = LocalDate.of(2026, 6, 10);
    Object[] row = new Object[] {10L, new BigDecimal("1000000")};
    given(settlementRepository.findGrossAmountByPeriodRaw(any(), any()))
        .willReturn(Collections.singletonList(row));
    given(settlementRepository.existsByStoreIdAndYearAndMonthAndHalf(10L, 2026, 6, 1))
        .willReturn(true); // 이미 존재

    // when
    int count = settlementService.processBatch(targetDate);

    // then: save 호출 없음
    assertThat(count).isEqualTo(0);
    then(settlementRepository).should(never()).save(any());
  }

  // ── listSettlements ──────────────────────────────────────────────────────────

  @Test
  void 정산_회차목록_조회_성공() {
    // given
    Long sellerId = 2L;
    Long storeId = 10L;
    Settlement s1 = SettlementFixture.aSettlement(store);
    Settlement s2 = SettlementFixture.aSettlementHalf2(store);
    given(storeService.requireOwnedStore(sellerId, storeId)).willReturn(store);
    given(settlementRepository.findByStoreIdOrderByYearDescMonthDescHalfDesc(storeId))
        .willReturn(List.of(s2, s1));
    given(settlementMapper.toCycleResponse(s2)).willReturn(SettlementFixture.aCycleResponse(2L));
    given(settlementMapper.toCycleResponse(s1)).willReturn(SettlementFixture.aCycleResponse(1L));

    // when
    List<SettlementCycleResponse> result = settlementService.listSettlements(sellerId, storeId);

    // then
    assertThat(result).hasSize(2);
    assertThat(result.get(0).id()).isEqualTo(2L);
    assertThat(result.get(1).id()).isEqualTo(1L);
  }

  // ── getSettlementSummary ─────────────────────────────────────────────────────

  @Test
  void 정산_요약_조회_성공_정산예정회차있음() {
    // given
    Long sellerId = 2L;
    Long storeId = 10L;
    Settlement s = SettlementFixture.aSettlement(store);
    given(storeService.requireOwnedStore(sellerId, storeId)).willReturn(store);
    given(
            settlementRepository.findTopByStoreIdAndStatusOrderByYearDescMonthDescHalfDesc(
                storeId, SettlementStatus.SCHEDULED))
        .willReturn(Optional.of(s));

    // when
    Optional<SettlementSummaryResponse> result =
        settlementService.getSettlementSummary(sellerId, storeId);

    // then
    assertThat(result).isPresent();
    SettlementSummaryResponse summary = result.get();
    assertThat(summary.cycleId()).isEqualTo(1L);
    assertThat(summary.periodLabel()).isEqualTo("6월 1차 · 6/1~6/15");
    assertThat(summary.netAmount()).isEqualByComparingTo("935000");
    assertThat(summary.status()).isEqualTo("SCHEDULED");
  }

  @Test
  void 정산_요약_조회_성공_회차없으면_null() {
    // given
    Long sellerId = 2L;
    Long storeId = 10L;
    given(storeService.requireOwnedStore(sellerId, storeId)).willReturn(store);
    given(
            settlementRepository.findTopByStoreIdAndStatusOrderByYearDescMonthDescHalfDesc(
                storeId, SettlementStatus.SCHEDULED))
        .willReturn(Optional.empty());

    // when
    Optional<SettlementSummaryResponse> result =
        settlementService.getSettlementSummary(sellerId, storeId);

    // then
    assertThat(result).isEmpty();
  }

  // ── 소유권 검증 ───────────────────────────────────────────────────────────────

  @Test
  void 매장소유권_검증_실패() {
    // given: 다른 사장 ID
    Long anotherSellerId = 99L;
    Long storeId = 10L;
    given(storeService.requireOwnedStore(anotherSellerId, storeId))
        .willThrow(new BusinessException(StoreErrorCode.STORE_ACCESS_DENIED));

    // when / then
    assertThatThrownBy(() -> settlementService.listSettlements(anotherSellerId, storeId))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(StoreErrorCode.STORE_ACCESS_DENIED);
  }

  // ── periodLabel 포맷 검증 ────────────────────────────────────────────────────

  @Test
  void periodLabel_포맷_검증() {
    // given: half=1 (6/1~6/15)
    Settlement s1 = SettlementFixture.aSettlement(store);
    assertThat(settlementService.buildPeriodLabel(s1)).isEqualTo("6월 1차 · 6/1~6/15");

    // given: half=2 (6/16~6/30)
    Settlement s2 = SettlementFixture.aSettlementHalf2(store);
    assertThat(settlementService.buildPeriodLabel(s2)).isEqualTo("6월 2차 · 6/16~6/30");
  }

  // ── 수수료 계산 ──────────────────────────────────────────────────────────────

  @Test
  void 수수료_계산_정확성() {
    // given
    LocalDate targetDate = LocalDate.of(2026, 6, 10);
    BigDecimal grossAmount = new BigDecimal("1000000");
    Object[] row = new Object[] {10L, grossAmount};
    given(settlementRepository.findGrossAmountByPeriodRaw(any(), any()))
        .willReturn(Collections.singletonList(row));
    given(
            settlementRepository.existsByStoreIdAndYearAndMonthAndHalf(
                anyLong(), anyInt(), anyInt(), anyInt()))
        .willReturn(false);
    given(storeRepository.getReferenceById(anyLong())).willReturn(store);

    ArgumentCaptor<Settlement> captor = ArgumentCaptor.forClass(Settlement.class);
    given(settlementRepository.save(captor.capture())).willAnswer(inv -> inv.getArgument(0));

    // when
    settlementService.processBatch(targetDate);

    // then: 1,000,000 * 0.065 = 65,000 (소수점 버림), net = 935,000
    Settlement saved = captor.getValue();
    assertThat(saved.getGrossAmount()).isEqualByComparingTo("1000000");
    assertThat(saved.getFeeAmount()).isEqualByComparingTo("65000");
    assertThat(saved.getNetAmount()).isEqualByComparingTo("935000");
  }

  // ── computeHalfPeriod 경계값 ─────────────────────────────────────────────────

  @Test
  void 반월_계산_1일은_half1() {
    var p = settlementService.computeHalfPeriod(LocalDate.of(2026, 6, 1));
    assertThat(p.half()).isEqualTo(1);
    assertThat(p.start()).isEqualTo(LocalDate.of(2026, 6, 1));
    assertThat(p.end()).isEqualTo(LocalDate.of(2026, 6, 15));
    assertThat(p.depositDate()).isEqualTo(LocalDate.of(2026, 6, 25));
  }

  @Test
  void 반월_계산_15일은_half1() {
    var p = settlementService.computeHalfPeriod(LocalDate.of(2026, 6, 15));
    assertThat(p.half()).isEqualTo(1);
  }

  @Test
  void 반월_계산_16일은_half2() {
    var p = settlementService.computeHalfPeriod(LocalDate.of(2026, 6, 16));
    assertThat(p.half()).isEqualTo(2);
    assertThat(p.start()).isEqualTo(LocalDate.of(2026, 6, 16));
    assertThat(p.end()).isEqualTo(LocalDate.of(2026, 6, 30));
    assertThat(p.depositDate()).isEqualTo(LocalDate.of(2026, 7, 10));
  }

  @Test
  void 반월_계산_12월_half2_익월_입금일() {
    var p = settlementService.computeHalfPeriod(LocalDate.of(2026, 12, 31));
    assertThat(p.half()).isEqualTo(2);
    assertThat(p.depositDate()).isEqualTo(LocalDate.of(2027, 1, 10));
  }
}
