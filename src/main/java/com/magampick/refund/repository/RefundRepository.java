package com.magampick.refund.repository;

import com.magampick.refund.domain.Refund;
import com.magampick.refund.domain.RefundStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefundRepository extends JpaRepository<Refund, Long> {

  /** 주문 ID 로 환불 단건 조회. */
  Optional<Refund> findByOrderId(Long orderId);

  /** 주문 ID + 상태로 환불 단건 조회. */
  Optional<Refund> findByOrderIdAndStatus(Long orderId, RefundStatus status);

  /** 주문 ID + 상태 목록으로 환불 존재 여부 확인. 포인트 확정 시 활성 환불(REQUESTED/APPROVED) 체크에 사용. */
  boolean existsByOrderIdAndStatusIn(Long orderId, Collection<RefundStatus> statuses);

  /** 자동 승인 배치 — REQUESTED 상태 + requestedAt < before 인 모든 환불. */
  List<Refund> findAllByStatusAndRequestedAtBefore(RefundStatus status, LocalDateTime before);

  /** 리마인드 배치 — REQUESTED + requestedAt < threshold + reminderSentAt IS NULL 인 환불. */
  List<Refund> findAllByStatusAndRequestedAtBeforeAndReminderSentAtIsNull(
      RefundStatus status, LocalDateTime threshold);

  /** 사장 매장 환불 목록 — storeId 기준, requestedAt 최신순. */
  List<Refund> findByOrderStoreIdOrderByRequestedAtDesc(Long storeId);
}
