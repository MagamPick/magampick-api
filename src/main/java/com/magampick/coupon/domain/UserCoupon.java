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

  /** 만료 방어판정: USABLE 인데 기준일 이전에 만료됐는가. */
  public boolean isExpiredAt(LocalDate date) {
    return status == CouponStatus.USABLE && expiresAt.isBefore(date);
  }

  @Builder
  private UserCoupon(
      Customer customer,
      Coupon coupon,
      CouponStatus status,
      LocalDate expiresAt,
      LocalDateTime issuedAt) {
    this.customer = customer;
    this.coupon = coupon;
    this.status = status != null ? status : CouponStatus.USABLE;
    this.expiresAt = expiresAt;
    this.issuedAt = issuedAt;
  }
}
