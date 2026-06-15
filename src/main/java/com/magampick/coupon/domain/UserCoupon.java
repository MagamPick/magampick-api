package com.magampick.coupon.domain;

import com.magampick.customer.domain.Customer;
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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 소비자에게 발급된 쿠폰 인스턴스. */
@Entity
@Table(name = "user_coupons")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserCoupon extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "customer_id", nullable = false)
  private Customer customer;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "coupon_id", nullable = false)
  private Coupon coupon;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private CouponStatus status;

  /** 쿠폰 유효기간 스냅샷 (발급 시점에 확정). */
  @Column(name = "expires_at", nullable = false)
  private LocalDate expiresAt;

  /** 발급 시각. */
  @Column(name = "issued_at", nullable = false)
  private LocalDateTime issuedAt;

  /** 사용 시각. 미사용 시 null. */
  @Column(name = "used_at")
  private LocalDateTime usedAt;

  /** 만료 7일 전 알림 발송 시각. 발송 전 null. */
  @Column(name = "expiry_alert_sent_at")
  private LocalDateTime expiryAlertSentAt;

  /** 발급 시점 할인 방식 스냅샷 (소급 방지). */
  @Enumerated(EnumType.STRING)
  @Column(name = "discount_type", nullable = false, length = 20)
  private CouponDiscountType discountType;

  /** 발급 시점 할인 값 스냅샷 (소급 방지). */
  @Column(name = "discount_value", nullable = false)
  private int discountValue;

  /** 발급 시점 최소 주문 금액 스냅샷 (소급 방지). */
  @Column(name = "min_order", nullable = false)
  private int minOrder;

  /** 소비자 소유권 판단. */
  public boolean isOwnedBy(Long customerId) {
    return customer.getId().equals(customerId);
  }

  public void markExpiryAlertSent(LocalDateTime now) {
    this.expiryAlertSentAt = now;
  }

  /** 만료 방어판정: USABLE 인데 기준일 이전에 만료됐는가. */
  public boolean isExpiredAt(LocalDate date) {
    return status == CouponStatus.USABLE && expiresAt.isBefore(date);
  }

  /** 쿠폰 복원 처리. USED → USABLE 전이. usedAt 초기화. 만료 여부는 호출자가 사전에 확인한다. */
  public void restore() {
    this.status = CouponStatus.USABLE;
    this.usedAt = null;
  }

  /**
   * 스냅샷 기반 주문 적용 가능 여부. menuSubtotal > 0 이고 발급 시점 minOrder 이상인 경우에만 가능.
   *
   * @param menuSubtotal 메뉴 소계 (쿠폰 할인 전 금액)
   * @return 적용 가능하면 true
   */
  public boolean isApplicableTo(BigDecimal menuSubtotal) {
    return menuSubtotal.signum() > 0 && menuSubtotal.compareTo(BigDecimal.valueOf(minOrder)) >= 0;
  }

  /**
   * 스냅샷 기반 할인 금액 계산. 발급 시점 discountType/discountValue 사용 (마스터 수정 소급 방지).
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
      return menuSubtotal
          .multiply(BigDecimal.valueOf(discountValue))
          .divide(BigDecimal.valueOf(100), 0, RoundingMode.FLOOR)
          .min(menuSubtotal);
    }
    return BigDecimal.valueOf(discountValue).min(menuSubtotal);
  }

  @Builder
  private UserCoupon(
      Customer customer,
      Coupon coupon,
      CouponStatus status,
      LocalDate expiresAt,
      LocalDateTime issuedAt,
      CouponDiscountType discountType,
      int discountValue,
      int minOrder) {
    this.customer = customer;
    this.coupon = coupon;
    this.status = status != null ? status : CouponStatus.USABLE;
    this.expiresAt = expiresAt;
    this.issuedAt = issuedAt;
    this.discountType = discountType;
    this.discountValue = discountValue;
    this.minOrder = minOrder;
  }
}
