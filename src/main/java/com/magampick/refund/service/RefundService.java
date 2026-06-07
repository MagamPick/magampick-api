package com.magampick.refund.service;

import com.magampick.global.exception.BusinessException;
import com.magampick.order.domain.Order;
import com.magampick.order.domain.OrderStatus;
import com.magampick.order.dto.OrderResponse;
import com.magampick.order.exception.OrderErrorCode;
import com.magampick.order.mapper.OrderMapper;
import com.magampick.order.repository.OrderRepository;
import com.magampick.refund.domain.Refund;
import com.magampick.refund.domain.RefundStatus;
import com.magampick.refund.dto.RefundInfoResponse;
import com.magampick.refund.dto.RefundRejectRequest;
import com.magampick.refund.dto.RefundRequestRequest;
import com.magampick.refund.dto.RefundResponse;
import com.magampick.refund.exception.RefundErrorCode;
import com.magampick.refund.mapper.RefundMapper;
import com.magampick.refund.repository.RefundRepository;
import com.magampick.store.exception.StoreErrorCode;
import com.magampick.store.repository.StoreRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 환불 도메인 서비스. Phase 6: 실결제 환불 연동 전까지 상태 기록 역할. */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RefundService {

  /** 환불 요청 가능 기간 (수령완료 후 일수). */
  private static final int REFUND_WINDOW_DAYS = 3;

  private final OrderRepository orderRepository;
  private final RefundRepository refundRepository;
  private final StoreRepository storeRepository;
  private final OrderMapper orderMapper;
  private final RefundMapper refundMapper;
  private final Clock clock;

  /**
   * 소비자 환불 요청. COMPLETED 주문만 / completedAt 후 3일 이내 / 1주문 1요청 / 사유 필수.
   *
   * @return OrderResponse (refund 필드 포함)
   */
  @Transactional
  public OrderResponse requestRefund(Long customerId, Long orderId, RefundRequestRequest request) {
    // ── 사유 검증 ─────────────────────────────────────────────────────────────
    if (request.reason() == null || request.reason().isBlank()) {
      throw new BusinessException(RefundErrorCode.REFUND_REASON_REQUIRED);
    }

    // ── 주문 조회 + 소유자 확인 ───────────────────────────────────────────────
    Order order =
        orderRepository
            .findById(orderId)
            .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
    if (!order.getCustomer().getId().equals(customerId)) {
      throw new BusinessException(OrderErrorCode.ORDER_FORBIDDEN);
    }

    // ── COMPLETED 상태 확인 ──────────────────────────────────────────────────
    if (order.getStatus() != OrderStatus.COMPLETED) {
      throw new BusinessException(RefundErrorCode.REFUND_NOT_COMPLETED_ORDER);
    }

    // ── 3일 이내 확인 ────────────────────────────────────────────────────────
    LocalDateTime completedAt = order.getCompletedAt();
    LocalDateTime now = LocalDateTime.now(clock);
    if (completedAt == null || now.isAfter(completedAt.plusDays(REFUND_WINDOW_DAYS))) {
      throw new BusinessException(RefundErrorCode.REFUND_WINDOW_EXPIRED);
    }

    // ── 중복 요청 확인 ───────────────────────────────────────────────────────
    if (refundRepository.findByOrderId(orderId).isPresent()) {
      throw new BusinessException(RefundErrorCode.REFUND_ALREADY_REQUESTED);
    }

    // ── 환불 생성 ────────────────────────────────────────────────────────────
    Refund refund = Refund.builder().order(order).reason(request.reason()).requestedAt(now).build();
    Refund savedRefund = refundRepository.save(refund);

    log.info(
        "환불 요청됨. refundId={}, orderId={}, customerId={}", savedRefund.getId(), orderId, customerId);

    // ── OrderResponse with refund 반환 ────────────────────────────────────────
    RefundInfoResponse refundInfo = refundMapper.toInfoResponse(savedRefund);
    OrderResponse base = orderMapper.toResponse(order);
    return orderMapper.withRefund(base, refundInfo);
  }

  /** 사장 매장 환불 목록 조회. 최신순. 매장 소유 확인 포함. */
  public List<RefundResponse> listStoreRefunds(Long sellerId, Long storeId) {
    storeRepository
        .findByIdAndSellerId(storeId, sellerId)
        .orElseThrow(() -> new BusinessException(StoreErrorCode.STORE_ACCESS_DENIED));

    return refundRepository.findByOrderStoreIdOrderByRequestedAtDesc(storeId).stream()
        .map(refundMapper::toResponse)
        .toList();
  }

  /** 사장 환불 승인. REQUESTED → APPROVED. 본인 매장 주문만 가능. */
  @Transactional
  public RefundResponse approveRefund(Long sellerId, Long refundId) {
    Refund refund = findRefundForSeller(sellerId, refundId);

    if (refund.getStatus() != RefundStatus.REQUESTED) {
      throw new BusinessException(RefundErrorCode.REFUND_ALREADY_PROCESSED);
    }

    refund.approve(LocalDateTime.now(clock));
    Refund saved = refundRepository.save(refund);

    log.info("환불 승인됨. refundId={}, sellerId={}", refundId, sellerId);
    return refundMapper.toResponse(saved);
  }

  /** 사장 환불 거부. REQUESTED → REJECTED. 본인 매장 주문만 가능. */
  @Transactional
  public RefundResponse rejectRefund(Long sellerId, Long refundId, RefundRejectRequest request) {
    // ── 거부 사유 검증 ────────────────────────────────────────────────────────
    if (request.rejectReason() == null || request.rejectReason().isBlank()) {
      throw new BusinessException(RefundErrorCode.REFUND_REJECT_REASON_REQUIRED);
    }

    Refund refund = findRefundForSeller(sellerId, refundId);

    if (refund.getStatus() != RefundStatus.REQUESTED) {
      throw new BusinessException(RefundErrorCode.REFUND_ALREADY_PROCESSED);
    }

    refund.reject(request.rejectReason(), LocalDateTime.now(clock));
    Refund saved = refundRepository.save(refund);

    log.info("환불 거부됨. refundId={}, sellerId={}", refundId, sellerId);
    return refundMapper.toResponse(saved);
  }

  /** 자동 승인 배치. requestedAt + 3일 이후인 REQUESTED 상태 환불 → APPROVED. 스케줄러에서 호출. */
  @Transactional
  public void autoApproveExpiredRefunds() {
    LocalDateTime threshold = LocalDateTime.now(clock).minusDays(REFUND_WINDOW_DAYS);
    List<Refund> targets =
        refundRepository.findAllByStatusAndRequestedAtBefore(RefundStatus.REQUESTED, threshold);

    if (targets.isEmpty()) {
      return;
    }

    LocalDateTime now = LocalDateTime.now(clock);
    for (Refund refund : targets) {
      refund.approve(now);
    }
    refundRepository.saveAll(targets);

    log.info("환불 자동 승인 배치 완료. 처리 건수={}", targets.size());
  }

  /** 사장 전용: 환불 조회 + 본인 매장 검증. 공통 헬퍼. */
  private Refund findRefundForSeller(Long sellerId, Long refundId) {
    Refund refund =
        refundRepository
            .findById(refundId)
            .orElseThrow(() -> new BusinessException(RefundErrorCode.REFUND_NOT_FOUND));
    if (!refund.getOrder().getStore().getSeller().getId().equals(sellerId)) {
      throw new BusinessException(RefundErrorCode.REFUND_FORBIDDEN);
    }
    return refund;
  }
}
