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

/** 포인트 내역. 적립·사용·만료·복원·회수 5사유. */
@Entity
@Table(name = "point_transactions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointTransaction extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "customer_id", nullable = false)
  private Customer customer;

  /** 연관 주문. 소멸(EXPIRE) 등 주문 무관 사유는 null. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "order_id")
  private Order order;

  @Enumerated(EnumType.STRING)
  @Column(name = "reason", nullable = false, length = 20)
  private PointReason reason;

  /** 포인트 변동량. 항상 양수. */
  @Column(name = "amount", nullable = false)
  private Long amount;

  /** 연관 매장 이름 스냅샷. 주문 없는 내역은 null. */
  @Column(name = "store_name", length = 100)
  private String storeName;

  /** 내역 발생 시각. */
  @Column(name = "occurred_at", nullable = false)
  private LocalDateTime occurredAt;

  @Builder
  private PointTransaction(
      Customer customer,
      Order order,
      PointReason reason,
      Long amount,
      String storeName,
      LocalDateTime occurredAt) {
    this.customer = customer;
    this.order = order;
    this.reason = reason;
    this.amount = amount;
    this.storeName = storeName;
    this.occurredAt = occurredAt;
  }
}
