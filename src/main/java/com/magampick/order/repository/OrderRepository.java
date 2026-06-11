package com.magampick.order.repository;

import com.magampick.order.domain.Order;
import com.magampick.order.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {

  /** 소비자 주문 목록 — customerId 기준, status 목록 필터, 최신순. */
  List<Order> findByCustomerIdAndStatusInOrderByCreatedAtDesc(
      Long customerId, List<OrderStatus> statuses);

  /** 사장 매장 주문 목록 — storeId 기준, status 목록 필터, 최신순. */
  List<Order> findByStoreIdAndStatusInOrderByCreatedAtDesc(
      Long storeId, List<OrderStatus> statuses);

  /** 소비자 COMPLETED 주문 목록 — Store + OrderItems JOIN FETCH (N+1 방지). completedAt 내림차순. */
  @Query(
      "SELECT DISTINCT o FROM Order o "
          + "JOIN FETCH o.store "
          + "JOIN FETCH o.orderItems oi "
          + "LEFT JOIN FETCH oi.clearanceItem "
          + "WHERE o.customer.id = :customerId AND o.status = :status "
          + "ORDER BY o.completedAt DESC")
  List<Order> findCompletedOrdersWithDetails(
      @Param("customerId") Long customerId, @Param("status") OrderStatus status);

  /**
   * 통계용 완료 주문 목록 — OrderItems JOIN FETCH, 환불승인 제외. completedAt [start, end) 범위.
   *
   * <p>NOT EXISTS 서브쿼리로 APPROVED 환불 있는 주문 제외. 매출·떨이 집계에 사용.
   */
  @Query(
      "SELECT DISTINCT o FROM Order o "
          + "LEFT JOIN FETCH o.orderItems "
          + "WHERE o.store.id = :storeId "
          + "AND o.status = com.magampick.order.domain.OrderStatus.COMPLETED "
          + "AND o.completedAt >= :start AND o.completedAt < :end "
          + "AND o.deletedAt IS NULL "
          + "AND NOT EXISTS ("
          + "  SELECT 1 FROM Refund rf "
          + "  WHERE rf.order = o "
          + "  AND rf.status = com.magampick.refund.domain.RefundStatus.APPROVED"
          + ")")
  List<Order> findCompletedForAnalytics(
      @Param("storeId") Long storeId,
      @Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end);

  /**
   * 통계용 완료 주문 총 금액 합계 — 환불승인 제외. completedAt [start, end) 범위.
   *
   * <p>결과 없으면 null 반환 — 호출 측에서 null→0 처리.
   */
  @Query(
      "SELECT SUM(o.totalPrice) FROM Order o "
          + "WHERE o.store.id = :storeId "
          + "AND o.status = com.magampick.order.domain.OrderStatus.COMPLETED "
          + "AND o.completedAt >= :start AND o.completedAt < :end "
          + "AND o.deletedAt IS NULL "
          + "AND NOT EXISTS ("
          + "  SELECT 1 FROM Refund rf "
          + "  WHERE rf.order = o "
          + "  AND rf.status = com.magampick.refund.domain.RefundStatus.APPROVED"
          + ")")
  BigDecimal sumCompletedTotalPrice(
      @Param("storeId") Long storeId,
      @Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end);

  /**
   * 통계용 주문 상태별 건수. createdAt [start, end) 범위, AWAITING_PAYMENT 제외.
   *
   * <p>결과 행: [OrderStatus, Long count]. AWAITING_PAYMENT 상태는 제외됨.
   */
  @Query(
      "SELECT o.status, COUNT(o) FROM Order o "
          + "WHERE o.store.id = :storeId "
          + "AND o.createdAt >= :start AND o.createdAt < :end "
          + "AND o.deletedAt IS NULL "
          + "AND o.status <> com.magampick.order.domain.OrderStatus.AWAITING_PAYMENT "
          + "GROUP BY o.status")
  List<Object[]> countOrdersByStatus(
      @Param("storeId") Long storeId,
      @Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end);

  /**
   * 소비자 마이페이지 통계용 — 이번 달 discountTotal 합계. completedAt KST [start, end) 범위.
   *
   * <p>마감할인(discountTotal)만 포함. 환불승인 제외. 결과 없으면 null — 호출 측에서 null→0 처리.
   */
  @Query(
      "SELECT SUM(o.discountTotal) FROM Order o "
          + "WHERE o.customer.id = :customerId "
          + "AND o.status = com.magampick.order.domain.OrderStatus.COMPLETED "
          + "AND o.completedAt >= :start AND o.completedAt < :end "
          + "AND o.deletedAt IS NULL "
          + "AND NOT EXISTS ("
          + "  SELECT 1 FROM Refund rf "
          + "  WHERE rf.order = o "
          + "  AND rf.status = com.magampick.refund.domain.RefundStatus.APPROVED"
          + ")")
  BigDecimal sumMonthlyDiscountTotal(
      @Param("customerId") Long customerId,
      @Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end);

  /**
   * 소비자 마이페이지 통계용 — 누적 음식 구출 수량. 환불승인 제외.
   *
   * <p>OrderItem.quantity 총합. 결과 없으면 null — 호출 측에서 null→0 처리.
   */
  @Query(
      "SELECT SUM(oi.quantity) FROM OrderItem oi "
          + "WHERE oi.order.customer.id = :customerId "
          + "AND oi.order.status = com.magampick.order.domain.OrderStatus.COMPLETED "
          + "AND oi.order.deletedAt IS NULL "
          + "AND NOT EXISTS ("
          + "  SELECT 1 FROM Refund rf "
          + "  WHERE rf.order = oi.order "
          + "  AND rf.status = com.magampick.refund.domain.RefundStatus.APPROVED"
          + ")")
  Long sumCumulativeRescuedItemQuantity(@Param("customerId") Long customerId);
}
