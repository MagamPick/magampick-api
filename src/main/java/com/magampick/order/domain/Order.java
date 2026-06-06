package com.magampick.order.domain;

import com.magampick.customer.domain.Customer;
import com.magampick.global.common.BaseEntity;
import com.magampick.store.domain.Store;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 주문. Phase 5 이전 스키마 선반영 — 상태 전이 로직 / 결제 컬럼 없음. 리뷰 read-only (Phase 4) 를 위해 최소 구현. */
@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "customer_id", nullable = false)
  private Customer customer;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "store_id", nullable = false)
  private Store store;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private OrderStatus status;

  @Column(name = "total_price", nullable = false, precision = 12, scale = 0)
  private BigDecimal totalPrice;

  @Column(name = "pickup_time")
  private LocalDateTime pickupTime;

  @Column(name = "deleted_at")
  private LocalDateTime deletedAt;

  @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<OrderItem> orderItems = new ArrayList<>();

  @Builder
  private Order(
      Customer customer,
      Store store,
      OrderStatus status,
      BigDecimal totalPrice,
      LocalDateTime pickupTime) {
    this.customer = customer;
    this.store = store;
    this.status = status != null ? status : OrderStatus.RECEIVED;
    this.totalPrice = totalPrice != null ? totalPrice : BigDecimal.ZERO;
    this.pickupTime = pickupTime;
  }

  public boolean isDeleted() {
    return deletedAt != null;
  }
}
