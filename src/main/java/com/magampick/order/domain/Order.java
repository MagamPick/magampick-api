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

/** 주문. Phase 5A: 주문 생성 + stub 결제 확정. */
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

  /** 결제액(payTotal). 쿠폰·포인트 미적용 = normalTotal - discountTotal. */
  @Column(name = "total_price", nullable = false, precision = 12, scale = 0)
  private BigDecimal totalPrice;

  /** SLOT 픽업 시각(KST). ASAP = null. */
  @Column(name = "pickup_time")
  private LocalDateTime pickupTime;

  @Column(name = "deleted_at")
  private LocalDateTime deletedAt;

  /** 픽업 유형 (ASAP / SLOT). Phase 5A 이전 주문은 null. */
  @Enumerated(EnumType.STRING)
  @Column(name = "pickup_type", length = 10)
  private PickupType pickupType;

  /** 픽업 인증 코드 4자리 숫자 문자열. */
  @Column(name = "pickup_code", length = 4)
  private String pickupCode;

  /** 픽업 요청 메모 (≤80자, 선택). */
  @Column(name = "memo", length = 80)
  private String memo;

  /** 사장 수락 시각 (nullable). */
  @Column(name = "accepted_at")
  private LocalDateTime acceptedAt;

  /** 준비완료 시각 (nullable). */
  @Column(name = "ready_at")
  private LocalDateTime readyAt;

  /** 수령완료 시각 (nullable). */
  @Column(name = "completed_at")
  private LocalDateTime completedAt;

  /** 사장 거절 시각 (nullable). */
  @Column(name = "rejected_at")
  private LocalDateTime rejectedAt;

  /** 소비자 취소 시각 (nullable). */
  @Column(name = "cancelled_at")
  private LocalDateTime cancelledAt;

  /** 정상가 합계 (모든 항목의 regularPrice × qty 합). */
  @Column(name = "normal_total", precision = 12, scale = 0)
  private BigDecimal normalTotal;

  /** 할인 합계 (떨이 항목의 (regularPrice - salePrice) × qty 합). */
  @Column(name = "discount_total", precision = 12, scale = 0)
  private BigDecimal discountTotal;

  @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<OrderItem> orderItems = new ArrayList<>();

  @Builder
  private Order(
      Customer customer,
      Store store,
      OrderStatus status,
      BigDecimal totalPrice,
      LocalDateTime pickupTime,
      PickupType pickupType,
      String pickupCode,
      String memo,
      BigDecimal normalTotal,
      BigDecimal discountTotal) {
    this.customer = customer;
    this.store = store;
    this.status = status != null ? status : OrderStatus.PENDING;
    this.totalPrice = totalPrice != null ? totalPrice : BigDecimal.ZERO;
    this.pickupTime = pickupTime;
    this.pickupType = pickupType;
    this.pickupCode = pickupCode;
    this.memo = memo;
    this.normalTotal = normalTotal;
    this.discountTotal = discountTotal;
  }

  public boolean isDeleted() {
    return deletedAt != null;
  }

  /** 주문 항목 추가. CascadeType.ALL 이 저장을 처리한다. */
  public void addOrderItem(OrderItem item) {
    this.orderItems.add(item);
  }

  // ── 상태 전이 메서드 (서비스에서 전이 가능 여부 검증 후 호출) ────────────────────────────

  /** 소비자 취소. PENDING → CANCELLED. */
  public void cancel(LocalDateTime now) {
    this.status = OrderStatus.CANCELLED;
    this.cancelledAt = now;
  }

  /** 사장 수락. PENDING → PREPARING. */
  public void accept(LocalDateTime now) {
    this.status = OrderStatus.PREPARING;
    this.acceptedAt = now;
  }

  /** 사장 거절. PENDING → REJECTED. */
  public void reject(LocalDateTime now) {
    this.status = OrderStatus.REJECTED;
    this.rejectedAt = now;
  }

  /** 준비완료. PREPARING → READY. */
  public void markReady(LocalDateTime now) {
    this.status = OrderStatus.READY;
    this.readyAt = now;
  }

  /** 수령완료. READY → COMPLETED. */
  public void complete(LocalDateTime now) {
    this.status = OrderStatus.COMPLETED;
    this.completedAt = now;
  }

  /** 미수령. READY → NO_SHOW. timestamp 없음. */
  public void noShow() {
    this.status = OrderStatus.NO_SHOW;
  }
}
