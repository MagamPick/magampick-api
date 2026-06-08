package com.magampick.point.domain;

import com.magampick.customer.domain.Customer;
import com.magampick.global.common.BaseEntity;
import com.magampick.order.domain.Order;
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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 포인트 적립 lot. FIFO 차감 방식으로 잔량(remainingAmount)을 관리한다. */
@Entity
@Table(name = "point_accruals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointAccrual extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "customer_id", nullable = false)
  private Customer customer;

  /** 적립 출처 주문. 비주문 적립(이벤트 등)은 null. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "order_id")
  private Order order;

  /** 최초 적립 포인트. 1P = 1원. */
  @Column(name = "initial_amount", nullable = false)
  private Long initialAmount;

  /** FIFO 차감 후 잔량. 0 이상 initialAmount 이하. */
  @Column(name = "remaining_amount", nullable = false)
  private Long remainingAmount;

  /** 적립 발생 시각. */
  @Column(name = "earned_at", nullable = false)
  private LocalDateTime earnedAt;

  /** 유효기간 만료 시각. earned_at + 1년. */
  @Column(name = "expires_at", nullable = false)
  private LocalDateTime expiresAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private PointAccrualStatus status;

  /**
   * FIFO 차감. 호출자는 amount ≤ remainingAmount 를 보장해야 한다. remainingAmount 가 0 이 되면 EXHAUSTED 로 전이한다.
   *
   * @param amount 차감할 포인트 (양수)
   */
  public void deduct(long amount) {
    this.remainingAmount -= amount;
    if (this.remainingAmount == 0) {
      this.status = PointAccrualStatus.EXHAUSTED;
    }
  }

  /** 유효기간 만료 소멸. remainingAmount 를 0 으로 설정하고 EXPIRED 로 전이한다. */
  public void expire() {
    this.remainingAmount = 0L;
    this.status = PointAccrualStatus.EXPIRED;
  }

  @Builder
  private PointAccrual(
      Customer customer,
      Order order,
      Long initialAmount,
      Long remainingAmount,
      LocalDateTime earnedAt,
      LocalDateTime expiresAt,
      PointAccrualStatus status) {
    this.customer = customer;
    this.order = order;
    this.initialAmount = initialAmount;
    this.remainingAmount = remainingAmount;
    this.earnedAt = earnedAt;
    this.expiresAt = expiresAt;
    this.status = status != null ? status : PointAccrualStatus.ACTIVE;
  }
}
