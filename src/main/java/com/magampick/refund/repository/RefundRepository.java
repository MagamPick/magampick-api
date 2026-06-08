package com.magampick.refund.repository;

import com.magampick.refund.domain.Refund;
import com.magampick.refund.domain.RefundStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefundRepository extends JpaRepository<Refund, Long> {

  /** 주문 ID 로 환불 단건 조회. */
  Optional<Refund> findByOrderId(Long orderId);

  /** 주문 ID + 상태로 환불 단건 조회. */
  Optional<Refund> findByOrderIdAndStatus(Long orderId, RefundStatus status);

  /** 자동 승인 배치 — REQUESTED 상태 + requestedAt < before 인 모든 환불. */
  List<Refund> findAllByStatusAndRequestedAtBefore(RefundStatus status, LocalDateTime before);

  /** 리마인드 배치 — REQUESTED + requestedAt < threshold + reminderSentAt IS NULL 인 환불. */
  List<Refund> findAllByStatusAndRequestedAtBeforeAndReminderSentAtIsNull(
      RefundStatus status, LocalDateTime threshold);

  /** 사장 매장 환불 목록 — storeId 기준, requestedAt 최신순. */
  List<Refund> findByOrderStoreIdOrderByRequestedAtDesc(Long storeId);
}
