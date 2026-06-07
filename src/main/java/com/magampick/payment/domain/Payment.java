package com.magampick.payment.domain;

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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 결제. 주문 1:1 (orders.id UNIQUE). Phase 5A: stub 자동승인 → APPROVED. */
@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "order_id", nullable = false, unique = true)
  private Order order;

  /** 결제 PG 제공사 (현재 TOSS 고정). */
  @Column(name = "provider", nullable = false, length = 10)
  private String provider;

  /** 결제 수단 (toss 고정). */
  @Column(name = "method", nullable = false, length = 20)
  private String method;

  /** PG 발급 결제 키 (stub = 가짜 UUID 형태). */
  @Column(name = "payment_key", nullable = false, length = 200)
  private String paymentKey;

  /** 결제 금액 (payTotal). */
  @Column(name = "amount", nullable = false, precision = 12, scale = 0)
  private BigDecimal amount;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private PaymentStatus status;

  /** 승인 시각 (APPROVED 상태에서 채워짐). */
  @Column(name = "approved_at")
  private LocalDateTime approvedAt;

  @Builder
  private Payment(
      Order order,
      String provider,
      String method,
      String paymentKey,
      BigDecimal amount,
      PaymentStatus status,
      LocalDateTime approvedAt) {
    this.order = order;
    this.provider = provider;
    this.method = method;
    this.paymentKey = paymentKey;
    this.amount = amount;
    this.status = status;
    this.approvedAt = approvedAt;
  }
}
