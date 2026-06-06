package com.magampick.order.fixture;

import com.magampick.clearance.domain.ClearanceItem;
import com.magampick.customer.domain.Customer;
import com.magampick.order.domain.Order;
import com.magampick.order.domain.OrderItem;
import com.magampick.order.domain.OrderStatus;
import com.magampick.store.domain.Store;
import java.math.BigDecimal;

public class OrderFixture {

  private OrderFixture() {}

  /** 기본 주문 (PICKED_UP 상태 — 리뷰 작성 가능 상태). */
  public static Order anOrder(Customer customer, Store store) {
    return Order.builder()
        .customer(customer)
        .store(store)
        .status(OrderStatus.PICKED_UP)
        .totalPrice(new BigDecimal("3000"))
        .build();
  }

  /** 지정 상태 주문. */
  public static Order anOrderWithStatus(Customer customer, Store store, OrderStatus status) {
    return Order.builder()
        .customer(customer)
        .store(store)
        .status(status)
        .totalPrice(new BigDecimal("3000"))
        .build();
  }

  /** 주문 항목 (떨이 상품 1개). Order 와 별도로 저장 필요. */
  public static OrderItem anOrderItem(Order order, ClearanceItem clearanceItem) {
    return OrderItem.builder()
        .order(order)
        .clearanceItem(clearanceItem)
        .quantity(1)
        .unitPrice(clearanceItem.getSalePrice())
        .build();
  }
}
