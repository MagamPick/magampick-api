package com.magampick.order.domain;

import com.magampick.clearance.domain.ClearanceItem;
import com.magampick.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 주문 항목. 떨이(clearance_item)만 참조 — 메뉴 상품 직접 주문 없음. */
@Entity
@Table(name = "order_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "order_id", nullable = false)
  private Order order;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "clearance_item_id", nullable = false)
  private ClearanceItem clearanceItem;

  @Column(name = "quantity", nullable = false)
  private int quantity;

  @Column(name = "unit_price", nullable = false, precision = 12, scale = 0)
  private BigDecimal unitPrice;

  @Column(name = "subtotal", nullable = false, precision = 12, scale = 0)
  private BigDecimal subtotal;

  @Builder
  private OrderItem(Order order, ClearanceItem clearanceItem, int quantity, BigDecimal unitPrice) {
    this.order = order;
    this.clearanceItem = clearanceItem;
    this.quantity = quantity;
    this.unitPrice = unitPrice;
    this.subtotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
  }
}
