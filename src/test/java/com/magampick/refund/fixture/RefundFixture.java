package com.magampick.refund.fixture;

import com.magampick.order.domain.Order;
import com.magampick.order.domain.OrderStatus;
import com.magampick.order.fixture.OrderFixture;
import com.magampick.refund.domain.Refund;
import com.magampick.refund.domain.RefundStatus;
import com.magampick.refund.dto.RefundInfoResponse;
import com.magampick.refund.dto.RefundItemResponse;
import com.magampick.refund.dto.RefundRejectRequest;
import com.magampick.refund.dto.RefundRequestRequest;
import com.magampick.refund.dto.RefundResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.test.util.ReflectionTestUtils;

/** 환불 도메인 테스트 픽스처. */
public class RefundFixture {

  private RefundFixture() {}

  // ── 도메인 객체 ─────────────────────────────────────────────────────────────

  /** COMPLETED 상태, completedAt = 현재 - 1일인 Order 픽스처. */
  public static Order aCompletedOrder() {
    Order order =
        OrderFixture.anOrderWithStatus(
            OrderFixture.aCustomer(), OrderFixture.aStore(), OrderStatus.COMPLETED);
    ReflectionTestUtils.setField(order, "id", 42L);
    ReflectionTestUtils.setField(order, "completedAt", LocalDateTime.now().minusDays(1));
    return order;
  }

  /** REQUESTED 상태, requestedAt = 현재 - 1시간인 Refund 픽스처. */
  public static Refund aRequestedRefund(Order order) {
    Refund refund =
        Refund.builder()
            .order(order)
            .reason("상품이 예상과 달랐어요")
            .requestedAt(LocalDateTime.now().minusHours(1))
            .build();
    ReflectionTestUtils.setField(refund, "id", 1L);
    return refund;
  }

  /** requestedAt = 현재 - 4일 (자동 승인 대상) Refund 픽스처. */
  public static Refund anExpiredRequestedRefund(Order order) {
    Refund refund =
        Refund.builder()
            .order(order)
            .reason("사유")
            .requestedAt(LocalDateTime.now().minusDays(4))
            .build();
    ReflectionTestUtils.setField(refund, "id", 2L);
    return refund;
  }

  /** APPROVED 상태 Refund 픽스처. */
  public static Refund anApprovedRefund(Order order) {
    Refund refund = aRequestedRefund(order);
    ReflectionTestUtils.setField(refund, "status", RefundStatus.APPROVED);
    ReflectionTestUtils.setField(refund, "resolvedAt", LocalDateTime.now());
    return refund;
  }

  // ── Request DTO ─────────────────────────────────────────────────────────────

  public static RefundRequestRequest aRefundRequest() {
    return new RefundRequestRequest("상품이 예상과 달랐어요");
  }

  public static RefundRejectRequest aRejectRequest() {
    return new RefundRejectRequest("픽업 완료 후 24시간이 경과했습니다");
  }

  // ── Response DTO ─────────────────────────────────────────────────────────────

  public static RefundInfoResponse aRefundInfoResponse() {
    return new RefundInfoResponse(
        "REQUESTED",
        "상품이 예상과 달랐어요",
        OffsetDateTime.of(2026, 6, 7, 10, 0, 0, 0, ZoneOffset.ofHours(9)),
        null,
        null);
  }

  public static RefundResponse aRefundResponse(Long refundId) {
    return new RefundResponse(
        refundId,
        42L,
        "0042",
        10L,
        "테스터",
        List.of(new RefundItemResponse("크로아상", 2, new BigDecimal("3000"))),
        new BigDecimal("6000"),
        OffsetDateTime.of(2026, 6, 6, 15, 0, 0, 0, ZoneOffset.ofHours(9)),
        "REQUESTED",
        "상품이 예상과 달랐어요",
        OffsetDateTime.of(2026, 6, 7, 10, 0, 0, 0, ZoneOffset.ofHours(9)),
        null,
        null);
  }
}
