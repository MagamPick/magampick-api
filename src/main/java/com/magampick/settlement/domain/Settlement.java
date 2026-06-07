package com.magampick.settlement.domain;

import com.magampick.global.common.BaseEntity;
import com.magampick.store.domain.Store;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 반월 정산 회차. store × year × month × half 유니크. */
@Entity
@Table(
    name = "settlements",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_settlements_store_period",
            columnNames = {"store_id", "year", "month", "half"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Settlement extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "store_id", nullable = false)
  private Store store;

  @Column(nullable = false)
  private int year;

  @Column(nullable = false)
  private int month;

  /** 반월 구분. 1 = 1~15일, 2 = 16~말일. */
  @Column(nullable = false)
  private int half;

  @Column(name = "period_start", nullable = false)
  private LocalDate periodStart;

  @Column(name = "period_end", nullable = false)
  private LocalDate periodEnd;

  @Column(name = "deposit_date", nullable = false)
  private LocalDate depositDate;

  @Column(name = "gross_amount", nullable = false, precision = 15, scale = 2)
  private BigDecimal grossAmount;

  @Column(name = "fee_amount", nullable = false, precision = 15, scale = 2)
  private BigDecimal feeAmount;

  @Column(name = "net_amount", nullable = false, precision = 15, scale = 2)
  private BigDecimal netAmount;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private SettlementStatus status;

  @Builder
  private Settlement(
      Store store,
      int year,
      int month,
      int half,
      LocalDate periodStart,
      LocalDate periodEnd,
      LocalDate depositDate,
      BigDecimal grossAmount,
      BigDecimal feeAmount,
      BigDecimal netAmount,
      SettlementStatus status) {
    this.store = store;
    this.year = year;
    this.month = month;
    this.half = half;
    this.periodStart = periodStart;
    this.periodEnd = periodEnd;
    this.depositDate = depositDate;
    this.grossAmount = grossAmount != null ? grossAmount : BigDecimal.ZERO;
    this.feeAmount = feeAmount != null ? feeAmount : BigDecimal.ZERO;
    this.netAmount = netAmount != null ? netAmount : BigDecimal.ZERO;
    this.status = status != null ? status : SettlementStatus.SCHEDULED;
  }
}
