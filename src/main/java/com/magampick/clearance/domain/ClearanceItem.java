package com.magampick.clearance.domain;

import com.magampick.global.common.BaseEntity;
import com.magampick.product.domain.Product;
import com.magampick.store.domain.Store;
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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "clearance_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClearanceItem extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "store_id", nullable = false)
  private Store store;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "product_id")
  private Product product;

  @Column(name = "name", nullable = false, length = 50)
  private String name;

  @Column(name = "regular_price", nullable = false, precision = 12, scale = 0)
  private BigDecimal regularPrice;

  @Column(name = "sale_price", nullable = false, precision = 12, scale = 0)
  private BigDecimal salePrice;

  @Column(name = "total_quantity", nullable = false)
  private int totalQuantity;

  @Column(name = "remaining_quantity", nullable = false)
  private int remainingQuantity;

  @Column(name = "pickup_start_at", nullable = false)
  private LocalDateTime pickupStartAt;

  @Column(name = "pickup_end_at", nullable = false)
  private LocalDateTime pickupEndAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private ClearanceItemStatus status;

  @Column(name = "closing_alert_sent_at")
  private LocalDateTime closingAlertSentAt;

  @Builder
  private ClearanceItem(
      Store store,
      Product product,
      String name,
      BigDecimal regularPrice,
      BigDecimal salePrice,
      int totalQuantity,
      LocalDateTime pickupStartAt,
      LocalDateTime pickupEndAt) {
    this.store = store;
    this.product = product;
    this.name = name;
    this.regularPrice = regularPrice;
    this.salePrice = salePrice;
    this.totalQuantity = totalQuantity;
    this.remainingQuantity = totalQuantity;
    this.pickupStartAt = pickupStartAt;
    this.pickupEndAt = pickupEndAt;
    this.status = ClearanceItemStatus.OPEN;
  }

  public BigDecimal getDiscountRate() {
    return BigDecimal.ONE
        .subtract(salePrice.divide(regularPrice, 4, RoundingMode.HALF_UP))
        .setScale(2, RoundingMode.HALF_UP);
  }

  public void update(BigDecimal salePrice, Integer totalQuantity, LocalDateTime pickupEndAt) {
    if (salePrice != null) this.salePrice = salePrice;
    if (totalQuantity != null) {
      this.totalQuantity = totalQuantity;
      // 주문 도메인 연결 전 — remainingQuantity 는 항상 totalQuantity 와 동일
      // orders 계층 5 연결 시 remaining = total - sold 로 재검토
      this.remainingQuantity = totalQuantity;
    }
    if (pickupEndAt != null) this.pickupEndAt = pickupEndAt;
  }

  public void close() {
    this.status = ClearanceItemStatus.CLOSED;
  }

  /** 마감 임박 알림 발송 완료 표시. */
  public void markClosingAlertSent(LocalDateTime now) {
    this.closingAlertSentAt = now;
  }
}
