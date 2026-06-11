package com.magampick.refund.service;

import com.magampick.coupon.service.CouponService;
import com.magampick.global.exception.BusinessException;
import com.magampick.notification.domain.NotificationCategory;
import com.magampick.notification.service.NotificationService;
import com.magampick.order.domain.Order;
import com.magampick.order.dto.OrderResponse;
import com.magampick.order.exception.OrderErrorCode;
import com.magampick.order.mapper.OrderMapper;
import com.magampick.order.repository.OrderRepository;
import com.magampick.point.service.PointService;
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
  private final PointService pointService;
  private final CouponService couponService;
  private final NotificationService notificationService;
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
    if (!order.isOwnedBy(customerId)) {
      throw new BusinessException(OrderErrorCode.ORDER_FORBIDDEN);
    }

    // ── COMPLETED 상태 확인 ──────────────────────────────────────────────────
    if (!order.isCompleted()) {
      throw new BusinessException(RefundErrorCode.REFUND_NOT_COMPLETED_ORDER);
    }

    // ── 3일 이내 확인 ────────────────────────────────────────────────────────
    LocalDateTime now = LocalDateTime.now(clock);
    if (order.isRefundWindowExpired(now, REFUND_WINDOW_DAYS)) {
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

    // ── 매장 사장에게 환불 요청 알림 ────────────────────────────────────────────
    Long sellerId = order.getStore().getSeller().getId();
    notificationService.notifySeller(
        sellerId,
        "refundRequest",
        NotificationCategory.REFUND,
        "환불 요청이 접수되었어요",
        order.getCustomer().getNickname() + "님이 환불을 요청했어요. 3일 내 처리해 주세요.",
        "/refunds");

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

    // 상태 확인
    if (!refund.isRequested()) {
      throw new BusinessException(RefundErrorCode.REFUND_ALREADY_PROCESSED);
    }

    // 상태 전이
    refund.approve(LocalDateTime.now(clock));
    Refund saved = refundRepository.save(refund);
    // 혜택 역전
    reverseBenefits(refund.getOrder());

    // 알림 발송
    notificationService.notifyCustomer(
        refund.getOrder().getCustomer().getId(),
        "orderRefund",
        NotificationCategory.ORDER,
        "환불이 완료되었어요",
        refund.getOrder().getStore().getName() + " 주문 환불이 완료되었어요.",
        "/orders");

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

    // 상태 확인
    if (!refund.isRequested()) {
      throw new BusinessException(RefundErrorCode.REFUND_ALREADY_PROCESSED);
    }

    // 상태 전이
    refund.reject(request.rejectReason(), LocalDateTime.now(clock));
    Refund saved = refundRepository.save(refund);

    // 알림 발송
    notificationService.notifyCustomer(
        refund.getOrder().getCustomer().getId(),
        "orderRefund",
        NotificationCategory.ORDER,
        "환불이 거부되었어요",
        refund.getRejectReason(),
        "/orders");

    log.info("환불 거부됨. refundId={}, sellerId={}", refundId, sellerId);
    return refundMapper.toResponse(saved);
  }

  /** 자동 승인 대상 환불 ID 목록 (requestedAt + 3일 경과한 REQUESTED). */
  @Transactional(readOnly = true)
  public List<Long> findAutoApproveTargetIds() {
    LocalDateTime threshold = LocalDateTime.now(clock).minusDays(REFUND_WINDOW_DAYS);
    return refundRepository
        .findAllByStatusAndRequestedAtBefore(RefundStatus.REQUESTED, threshold)
        .stream()
        .map(Refund::getId)
        .toList();
  }

  /** D+2 리마인드 대상 환불 목록. REQUESTED + requestedAt 2일 이전 + reminderSentAt IS NULL. */
  @Transactional(readOnly = true)
  public List<Refund> findReminderTargets() {
    LocalDateTime threshold = LocalDateTime.now(clock).minusDays(2);
    return refundRepository.findAllByStatusAndRequestedAtBeforeAndReminderSentAtIsNull(
        RefundStatus.REQUESTED, threshold);
  }

  /** 리마인드 발송 + reminderSentAt 기록. 독립 트랜잭션(스케줄러가 건별 호출). 이미 발송됐거나 처리된 건은 skip. */
  @Transactional
  public void sendReminderAndMark(Long refundId) {
    // 환불 조회
    Refund refund =
        refundRepository
            .findById(refundId)
            .orElseThrow(() -> new BusinessException(RefundErrorCode.REFUND_NOT_FOUND));
    // 처리 여부 확인
    if (!refund.isRequested() || refund.getReminderSentAt() != null) {
      return; // 이미 처리됐거나 리마인드 발송 완료 — skip
    }

    // 리마인드 알림 발송
    Long sellerId = refund.getOrder().getStore().getSeller().getId();
    notificationService.notifySeller(
        sellerId,
        "refundRequest",
        NotificationCategory.REFUND,
        "환불 처리 기한이 하루 남았어요",
        "내일까지 처리하지 않으면 자동 승인됩니다.",
        "/refunds");

    // 리마인드 완료 기록
    refund.markReminderSent(LocalDateTime.now(clock));
    refundRepository.save(refund);
    log.info("환불 리마인드 발송됨. refundId={}", refundId);
  }

  /** 단건 자동 승인 + 혜택 정리. 독립 트랜잭션(스케줄러가 건별 호출). */
  @Transactional
  public void approveAndReverse(Long refundId) {
    // 환불 조회
    Refund refund =
        refundRepository
            .findById(refundId)
            .orElseThrow(() -> new BusinessException(RefundErrorCode.REFUND_NOT_FOUND));
    // 처리 여부 확인
    if (!refund.isRequested()) {
      return; // 이미 처리됨 — skip
    }
    // 상태 전이
    refund.approve(LocalDateTime.now(clock));
    refundRepository.save(refund);
    // 혜택 역전
    reverseBenefits(refund.getOrder());

    // 알림 발송
    notificationService.notifyCustomer(
        refund.getOrder().getCustomer().getId(),
        "orderRefund",
        NotificationCategory.ORDER,
        "환불이 완료되었어요",
        refund.getOrder().getStore().getName() + " 주문 환불이 완료되었어요.",
        "/orders");

    log.info("환불 자동 승인됨. refundId={}", refundId);
  }

  /**
   * 환불 승인 시 혜택 정리: 적립분 회수(먼저) → 사용 포인트 복원 → 쿠폰 복원.
   *
   * <p>clawback 을 먼저 실행하는 이유: findByOrderId 가 EARN lot 과 (restore 후 생길) RESTORE lot 을 모두 반환하므로,
   * restore 로 새 lot 이 생기기 전에 clawback 을 완료해야 EARN lot 만 대상이 된다.
   */
  private void reverseBenefits(Order order) {
    pointService.clawback(order);
    if (order.hasUsedPoints()) pointService.restore(order, order.getPointUsed());
    if (order.hasCoupon()) couponService.restore(order.getUserCouponId());
  }

  /** 사장 전용: 환불 조회 + 본인 매장 검증. 공통 헬퍼. */
  private Refund findRefundForSeller(Long sellerId, Long refundId) {
    Refund refund =
        refundRepository
            .findById(refundId)
            .orElseThrow(() -> new BusinessException(RefundErrorCode.REFUND_NOT_FOUND));
    if (!refund.getOrder().getStore().isOwnedBy(sellerId)) {
      throw new BusinessException(RefundErrorCode.REFUND_FORBIDDEN);
    }
    return refund;
  }
}
