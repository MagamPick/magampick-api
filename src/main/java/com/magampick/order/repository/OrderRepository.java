package com.magampick.order.repository;

import com.magampick.order.domain.Order;
import com.magampick.order.domain.OrderStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

  /** 소비자 주문 목록 — customerId 기준, status 목록 필터, 최신순. */
  List<Order> findByCustomerIdAndStatusInOrderByCreatedAtDesc(
      Long customerId, List<OrderStatus> statuses);

  /** 사장 매장 주문 목록 — storeId 기준, status 목록 필터, 최신순. */
  List<Order> findByStoreIdAndStatusInOrderByCreatedAtDesc(
      Long storeId, List<OrderStatus> statuses);
}
