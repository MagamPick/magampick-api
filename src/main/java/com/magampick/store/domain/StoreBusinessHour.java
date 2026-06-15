package com.magampick.store.domain;

import com.magampick.global.common.BaseEntity;
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
import java.time.DayOfWeek;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 매장의 요일별 영업시간. <strong>영업 요일만 row 가 존재</strong>한다 — 휴무 요일은 row 자체가 없음. 노션: 영업시간 설정 / 매장 영업 상태 관리
 * (OPEN 전환 조건으로 사용).
 */
@Entity
@Table(name = "store_business_hours")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoreBusinessHour extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "store_id", nullable = false)
  private Store store;

  @Enumerated(EnumType.STRING)
  @Column(name = "day_of_week", nullable = false, length = 10)
  private DayOfWeek dayOfWeek;

  @Column(name = "open_time", nullable = false)
  private LocalTime openTime;

  @Column(name = "close_time", nullable = false)
  private LocalTime closeTime;

  @Builder
  private StoreBusinessHour(
      Store store, DayOfWeek dayOfWeek, LocalTime openTime, LocalTime closeTime) {
    this.store = store;
    this.dayOfWeek = dayOfWeek;
    this.openTime = openTime;
    this.closeTime = closeTime;
  }
}
