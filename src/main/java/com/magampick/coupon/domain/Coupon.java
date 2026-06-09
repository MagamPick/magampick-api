package com.magampick.coupon.domain;

import com.magampick.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 쿠폰 마스터/템플릿. SIGNUP(가입 축하) / EVENT(이벤트) 두 종류를 지원한다. */
@Entity
@Table(name = "coupons")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Coupon extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(name = "kind", nullable = false, length = 20)
  private CouponKind kind;

  @Column(name = "label", nullable = false, length = 100)
  private String label;

  @Enumerated(EnumType.STRING)
  @Column(name = "discount_type", nullable = false, length = 20)
  private CouponDiscountType discountType;

  /** 할인 값 (RATE 면 %, AMOUNT 면 원). */
  @Column(name = "discount_value", nullable = false)
  private int discountValue;

  /** 최소 주문 금액 (원). */
  @Column(name = "min_order", nullable = false)
  private int minOrder;

  /** EVENT 고정 만료일. SIGNUP 은 null. */
  @Column(name = "valid_until")
  private LocalDate validUntil;

  /** SIGNUP 상대 유효기간(일). EVENT 는 null. */
  @Column(name = "validity_days")
  private Integer validityDays;

  /** 발급 한도. null = 무제한. */
  @Column(name = "issue_limit")
  private Integer issueLimit;

  /** 누적 발급 수. 원자적 증가는 @Modifying 쿼리로 수행. */
  @Column(name = "issued_count", nullable = false)
  private int issuedCount;

  @Column(name = "active", nullable = false)
  private boolean active;

  /** 이벤트 노출 시작일 (EVENT 전용, nullable). */
  @Column(name = "display_start_at")
  private LocalDate displayStartAt;

  /** 이벤트 노출 종료일 (EVENT 전용, nullable). */
  @Column(name = "display_end_at")
  private LocalDate displayEndAt;

  /**
   * 주문에 쿠폰 적용 가능 여부. menuSubtotal > 0 이고 minOrder 이상인 경우에만 가능.
   *
   * @param menuSubtotal 메뉴 소계 (쿠폰 할인 전 금액)
   * @return 적용 가능하면 true
   */
  public boolean isApplicableTo(BigDecimal menuSubtotal) {
    return menuSubtotal.signum() > 0 && menuSubtotal.compareTo(BigDecimal.valueOf(minOrder)) >= 0;
  }

  /**
   * 쿠폰 할인 금액 계산.
   *
   * <ul>
   *   <li>RATE: menuSubtotal × discountValue / 100 (내림, 1원 미만 버림)
   *   <li>AMOUNT: min(discountValue, menuSubtotal)
   * </ul>
   *
   * @param menuSubtotal 메뉴 소계 (쿠폰 할인 전 금액)
   * @return 할인 금액
   */
  public BigDecimal calcDiscount(BigDecimal menuSubtotal) {
    if (discountType == CouponDiscountType.RATE) {
      // discountValue > 100 이라도 menuSubtotal 을 초과하지 않도록 클램프
      return menuSubtotal
          .multiply(BigDecimal.valueOf(discountValue))
          .divide(BigDecimal.valueOf(100), 0, RoundingMode.FLOOR)
          .min(menuSubtotal);
    }
    // AMOUNT
    return BigDecimal.valueOf(discountValue).min(menuSubtotal);
  }

  /**
   * EVENT 상태 도출. active=false → ENDED. 그 외 today 기준 기간 비교. (SIGNUP 엔 의미 없음)
   *
   * @param today 기준 날짜
   * @return 이벤트 상태
   */
  public EventStatus eventStatus(LocalDate today) {
    if (!active) return EventStatus.ENDED;
    if (displayStartAt != null && today.isBefore(displayStartAt)) return EventStatus.SCHEDULED;
    if (displayEndAt != null && today.isAfter(displayEndAt)) return EventStatus.ENDED;
    return EventStatus.ONGOING;
  }

  /**
   * 소비자 노출/클레임 가능 = 진행중.
   *
   * @param today 기준 날짜
   * @return 진행중이면 true
   */
  public boolean isOngoing(LocalDate today) {
    return eventStatus(today) == EventStatus.ONGOING;
  }

  /**
   * 노출 기간/필드 부분 수정 — null 인자는 무시(변경 없음).
   *
   * @param label 쿠폰 이름
   * @param discountType 할인 방식
   * @param discountValue 할인 값
   * @param minOrder 최소 주문 금액
   * @param validUntil 고정 만료일
   * @param issueLimit 발급 한도
   * @param displayStartAt 이벤트 노출 시작일
   * @param displayEndAt 이벤트 노출 종료일
   */
  public void updateEvent(
      String label,
      CouponDiscountType discountType,
      Integer discountValue,
      Integer minOrder,
      LocalDate validUntil,
      Integer issueLimit,
      LocalDate displayStartAt,
      LocalDate displayEndAt) {
    if (label != null) this.label = label;
    if (discountType != null) this.discountType = discountType;
    if (discountValue != null) this.discountValue = discountValue;
    if (minOrder != null) this.minOrder = minOrder;
    if (validUntil != null) this.validUntil = validUntil;
    if (issueLimit != null) this.issueLimit = issueLimit;
    if (displayStartAt != null) this.displayStartAt = displayStartAt;
    if (displayEndAt != null) this.displayEndAt = displayEndAt;
  }

  /** 조기 종료. active=false → eventStatus=ENDED. */
  public void end() {
    this.active = false;
  }

  @Builder
  private Coupon(
      CouponKind kind,
      String label,
      CouponDiscountType discountType,
      int discountValue,
      int minOrder,
      LocalDate validUntil,
      Integer validityDays,
      Integer issueLimit,
      Boolean active,
      LocalDate displayStartAt,
      LocalDate displayEndAt) {
    this.kind = kind;
    this.label = label;
    this.discountType = discountType;
    this.discountValue = discountValue;
    this.minOrder = minOrder;
    this.validUntil = validUntil;
    this.validityDays = validityDays;
    this.issueLimit = issueLimit;
    this.issuedCount = 0;
    this.active = active != null ? active : true;
    this.displayStartAt = displayStartAt;
    this.displayEndAt = displayEndAt;
  }
}
