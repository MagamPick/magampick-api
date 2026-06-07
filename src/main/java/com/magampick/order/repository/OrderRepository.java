package com.magampick.order.repository;

import com.magampick.order.domain.Order;
import com.magampick.order.domain.OrderStatus;
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
}
