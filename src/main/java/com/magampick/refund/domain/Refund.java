package com.magampick.refund.domain;

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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 환불 엔티티. Order 와 1:1 단방향 — Refund 가 orders.id 를 FK 로 보유. Order 엔티티에 역방향 추가 X.
 *
 * <p>Phase 6: 실결제 환불 연동 전까지 상태 기록 역할.
 */
@Entity
@Table(name = "refunds")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Refund extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "order_id", nullable = false, unique = true)
  private Order order;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private RefundStatus status;

  @Column(name = "reason", nullable = false, length = 200)
  private String reason;

  @Column(name = "reject_reason", length = 200)
  private String rejectReason;

  @Column(name = "requested_at", nullable = false)
  private LocalDateTime requestedAt;

  @Column(name = "resolved_at")
  private LocalDateTime resolvedAt;

  @Column(name = "reminder_sent_at")
  private LocalDateTime reminderSentAt;

  @Builder
  private Refund(Order order, String reason, LocalDateTime requestedAt) {
    this.order = order;
    this.reason = reason;
    this.status = RefundStatus.REQUESTED;
    this.requestedAt = requestedAt;
  }

  /** 환불 승인. REQUESTED → APPROVED. */
  public void approve(LocalDateTime now) {
    this.status = RefundStatus.APPROVED;
    this.resolvedAt = now;
  }

  /** 환불 거부. REQUESTED → REJECTED. */
  public void reject(String rejectReason, LocalDateTime now) {
    this.status = RefundStatus.REJECTED;
    this.rejectReason = rejectReason;
    this.resolvedAt = now;
  }

  /** 리마인드 발송 완료 표시. 중복 발송 방지용. */
  public void markReminderSent(LocalDateTime now) {
    this.reminderSentAt = now;
  }
}
