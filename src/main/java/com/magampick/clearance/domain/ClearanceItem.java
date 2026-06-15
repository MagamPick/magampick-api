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

  @Enumerated(EnumType.STRING)
  @Column(name = "close_reason", length = 30)
  private ClearanceCloseReason closeReason;

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

  /** 판매 중(OPEN) 상태 판단. */
  public boolean isOpen() {
    return status == ClearanceItemStatus.OPEN;
  }

  /** 마감(CLOSED) 상태 판단. */
  public boolean isClosed() {
    return status == ClearanceItemStatus.CLOSED;
  }

  /** 품절(SOLD_OUT) 상태 판단. */
  public boolean isSoldOut() {
    return status == ClearanceItemStatus.SOLD_OUT;
  }

  public BigDecimal getDiscountRate() {
    return BigDecimal.ONE
        .subtract(salePrice.divide(regularPrice, 4, RoundingMode.HALF_UP))
        .setScale(2, RoundingMode.HALF_UP);
  }

  public void update(BigDecimal salePrice, Integer remainingQuantity, LocalDateTime pickupEndAt) {
    if (salePrice != null) this.salePrice = salePrice;
    if (remainingQuantity != null) {
      // 사장은 "남은 개수"만 수정 — 판매분(sold)은 보존하고 등록 수량을 재계산한다.
      // sold = 변경 전 등록 수량 − 변경 전 남은 수량 (불변식: total = sold + remaining)
      int sold = this.totalQuantity - this.remainingQuantity;
      this.remainingQuantity = remainingQuantity;
      this.totalQuantity = sold + remainingQuantity;
    }
    if (pickupEndAt != null) this.pickupEndAt = pickupEndAt;
  }

  /**
   * @deprecated 테스트 셋업 전용. 프로덕션 코드는 {@link #close(ClearanceCloseReason)} 사용.
   */
  @Deprecated
  public void close() {
    this.status = ClearanceItemStatus.CLOSED;
  }

  public void close(ClearanceCloseReason reason) {
    this.status = ClearanceItemStatus.CLOSED;
    this.closeReason = reason;
  }

  /** 마감 임박 알림 발송 완료 표시. */
  public void markClosingAlertSent(LocalDateTime now) {
    this.closingAlertSentAt = now;
  }
}
