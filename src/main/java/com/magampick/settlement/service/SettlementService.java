package com.magampick.settlement.service;

import com.magampick.global.exception.BusinessException;
import com.magampick.settlement.domain.Settlement;
import com.magampick.settlement.domain.SettlementStatus;
import com.magampick.settlement.dto.SettlementCycleResponse;
import com.magampick.settlement.dto.SettlementSummaryResponse;
import com.magampick.settlement.exception.SettlementErrorCode;
import com.magampick.settlement.mapper.SettlementMapper;
import com.magampick.settlement.repository.SettlementRepository;
import com.magampick.store.domain.Store;
import com.magampick.store.repository.StoreRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 정산 서비스. 배치 처리 + 사장 조회. */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettlementService {

  /** 플랫폼 수수료율: 6.5%. */
  private static final BigDecimal FEE_RATE = new BigDecimal("0.065");

  private final SettlementRepository settlementRepository;
  private final StoreRepository storeRepository;
  private final SettlementMapper settlementMapper;
  private final Clock clock;

  /**
   * 정산 배치 처리. targetDate 기반으로 반월 회차를 결정하고 완료 주문을 집계해 Settlement 를 생성한다.
   *
   * <p>이미 존재하는 회차(store × year × month × half)는 스킵.
   *
   * @param targetDate 반월 판별 기준 날짜 (1~15 → half=1, 16~말일 → half=2)
   * @return 새로 생성된 Settlement 수
   */
  @Transactional
  public int processBatch(LocalDate targetDate) {
    HalfPeriod period = computeHalfPeriod(targetDate);

    LocalDateTime periodStartDt = period.start().atStartOfDay();
    LocalDateTime periodEndDt = period.end().atTime(23, 59, 59);

    List<Object[]> aggregates =
        settlementRepository.findGrossAmountByPeriodRaw(periodStartDt, periodEndDt);

    LocalDate today = LocalDate.now(clock);
    int count = 0;

    for (Object[] row : aggregates) {
      Long storeId = ((Number) row[0]).longValue();
      BigDecimal grossAmount = (BigDecimal) row[1];

      // 같은 회차 중복 스킵
      if (settlementRepository.existsByStoreIdAndYearAndMonthAndHalf(
          storeId, period.year(), period.month(), period.half())) {
        log.debug(
            "정산 회차 중복 스킵. storeId={}, year={}, month={}, half={}",
            storeId,
            period.year(),
            period.month(),
            period.half());
        continue;
      }

      BigDecimal feeAmount = grossAmount.multiply(FEE_RATE).setScale(0, RoundingMode.DOWN);
      BigDecimal netAmount = grossAmount.subtract(feeAmount);

      // 입금일 <= 오늘이면 이미 입금 완료
      SettlementStatus status =
          !period.depositDate().isAfter(today)
              ? SettlementStatus.DEPOSITED
              : SettlementStatus.SCHEDULED;

      Store store = storeRepository.getReferenceById(storeId);

      Settlement settlement =
          Settlement.builder()
              .store(store)
              .year(period.year())
              .month(period.month())
              .half(period.half())
              .periodStart(period.start())
              .periodEnd(period.end())
              .depositDate(period.depositDate())
              .grossAmount(grossAmount)
              .feeAmount(feeAmount)
              .netAmount(netAmount)
              .status(status)
              .build();

      settlementRepository.save(settlement);
      count++;

      log.info(
          "정산 생성. storeId={}, period={}/{}-{}, gross={}, fee={}, net={}",
          storeId,
          period.year(),
          period.month(),
          period.half(),
          grossAmount,
          feeAmount,
          netAmount);
    }

    return count;
  }

  /** 사장 매장 정산 회차 목록. 본인 소유 매장만 허용. 최신순(year·month·half desc). */
  public List<SettlementCycleResponse> listSettlements(Long sellerId, Long storeId) {
    verifyStoreOwnership(sellerId, storeId);
    return settlementRepository.findByStoreIdOrderByYearDescMonthDescHalfDesc(storeId).stream()
        .map(settlementMapper::toCycleResponse)
        .toList();
  }

  /**
   * 사장 매장 정산 요약 카드. 가장 최근 SCHEDULED 회차 반환. 없으면 empty.
   *
   * <p>컨트롤러에서 empty → 204 No Content 처리.
   */
  public Optional<SettlementSummaryResponse> getSettlementSummary(Long sellerId, Long storeId) {
    verifyStoreOwnership(sellerId, storeId);
    return settlementRepository
        .findTopByStoreIdAndStatusOrderByYearDescMonthDescHalfDesc(
            storeId, SettlementStatus.SCHEDULED)
        .map(
            s ->
                new SettlementSummaryResponse(
                    s.getId(),
                    buildPeriodLabel(s),
                    s.getNetAmount(),
                    s.getDepositDate().atStartOfDay().atOffset(ZoneOffset.ofHours(9)),
                    s.getStatus().name()));
  }

  // ── 내부 유틸 ──────────────────────────────────────────────────────────────────

  private void verifyStoreOwnership(Long sellerId, Long storeId) {
    storeRepository
        .findByIdAndSellerId(storeId, sellerId)
        .orElseThrow(() -> new BusinessException(SettlementErrorCode.SETTLEMENT_STORE_FORBIDDEN));
  }

  /**
   * periodLabel 생성. "M월 N차 · M/D1~M/D2" 형식.
   *
   * <p>예: "6월 1차 · 6/1~6/15", "6월 2차 · 6/16~6/30"
   */
  String buildPeriodLabel(Settlement settlement) {
    int m = settlement.getMonth();
    int h = settlement.getHalf();
    int d1 = settlement.getPeriodStart().getDayOfMonth();
    int d2 = settlement.getPeriodEnd().getDayOfMonth();
    return m + "월 " + h + "차 · " + m + "/" + d1 + "~" + m + "/" + d2;
  }

  /**
   * targetDate 로부터 반월 회차 경계 계산.
   *
   * <ul>
   *   <li>day ≤ 15 → half=1, 1일~15일, 입금일 = 당월 25일
   *   <li>day ≥ 16 → half=2, 16일~말일, 입금일 = 익월 10일
   * </ul>
   */
  HalfPeriod computeHalfPeriod(LocalDate date) {
    int year = date.getYear();
    int month = date.getMonthValue();
    int day = date.getDayOfMonth();

    if (day <= 15) {
      LocalDate start = LocalDate.of(year, month, 1);
      LocalDate end = LocalDate.of(year, month, 15);
      LocalDate depositDate = LocalDate.of(year, month, 25);
      return new HalfPeriod(year, month, 1, start, end, depositDate);
    } else {
      LocalDate start = LocalDate.of(year, month, 16);
      LocalDate end = date.withDayOfMonth(date.lengthOfMonth());
      LocalDate nextMonth = date.plusMonths(1);
      LocalDate depositDate = LocalDate.of(nextMonth.getYear(), nextMonth.getMonthValue(), 10);
      return new HalfPeriod(year, month, 2, start, end, depositDate);
    }
  }

  /** 반월 회차 내부 표현. */
  record HalfPeriod(
      int year, int month, int half, LocalDate start, LocalDate end, LocalDate depositDate) {}
}
