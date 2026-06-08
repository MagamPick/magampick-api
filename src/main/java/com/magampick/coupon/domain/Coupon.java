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
      Boolean active) {
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
  }
}
