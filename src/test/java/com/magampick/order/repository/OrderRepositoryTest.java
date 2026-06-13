package com.magampick.order.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.magampick.TestcontainersConfiguration;
import com.magampick.clearance.domain.ClearanceItem;
import com.magampick.clearance.repository.ClearanceItemRepository;
import com.magampick.customer.domain.Customer;
import com.magampick.customer.repository.CustomerRepository;
import com.magampick.global.common.GeometryUtil;
import com.magampick.global.config.JpaAuditingConfig;
import com.magampick.order.domain.Order;
import com.magampick.order.domain.OrderItem;
import com.magampick.order.domain.OrderStatus;
import com.magampick.order.domain.PickupType;
import com.magampick.refund.domain.Refund;
import com.magampick.refund.repository.RefundRepository;
import com.magampick.seller.domain.Seller;
import com.magampick.seller.repository.SellerRepository;
import com.magampick.store.domain.OperationStatus;
import com.magampick.store.domain.Store;
import com.magampick.store.repository.StoreRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
class OrderRepositoryTest {

  @Autowired OrderRepository orderRepository;
  @Autowired ClearanceItemRepository clearanceItemRepository;
  @Autowired SellerRepository sellerRepository;
  @Autowired StoreRepository storeRepository;
  @Autowired CustomerRepository customerRepository;
  @Autowired RefundRepository refundRepository;

  private Store store;
  private Customer customer;

  @BeforeEach
  void setUp() {
    Seller seller =
        sellerRepository.save(
            Seller.builder()
                .email("seller_" + System.nanoTime() + "@test.com")
                .passwordHash("x")
                .ownerName("테스트사장")
                .build());
    store =
        storeRepository.save(
            Store.builder()
                .seller(seller)
                .businessNumber("1234567890")
                .representativeName("홍길동")
                .openDate(LocalDate.of(2024, 3, 15))
                .name("테스트매장")
                .roadAddress("서울시 중구 테스트로 1")
                .zonecode("04524")
                .location(GeometryUtil.toPoint(37.5665, 126.9780))
                .phone("02-1234-5678")
                .operationStatus(OperationStatus.OPEN)
                .build());
    customer =
        customerRepository.save(
            Customer.builder()
                .email("customer_" + System.nanoTime() + "@test.com")
                .passwordHash("x")
                .nickname("테스트소비자")
                .build());
  }

  @Test
  void cancelIfAwaitingPayment_AWAITING_주문_CANCELLED_전이_성공() {
    Order order = orderRepository.save(orderWithStatus(OrderStatus.AWAITING_PAYMENT));

    int affected = orderRepository.cancelIfAwaitingPayment(order.getId(), LocalDateTime.now());

    assertThat(affected).isEqualTo(1);
    Order updated = orderRepository.findById(order.getId()).orElseThrow();
    assertThat(updated.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    assertThat(updated.getCancelledAt()).isNotNull();
  }

  @Test
  void cancelIfAwaitingPayment_이미_전이된_주문은_0_반환() {
    Order order = orderRepository.save(orderWithStatus(OrderStatus.CANCELLED));

    int affected = orderRepository.cancelIfAwaitingPayment(order.getId(), LocalDateTime.now());

    assertThat(affected).isEqualTo(0);
  }

  @Test
  void cancelIfAwaitingPayment_후_incrementStock_재고_정상_복원() {
    // 떨이 재고 5개, 주문 시 1개 차감 → remainingQuantity=4
    ClearanceItem ci = clearanceItemRepository.save(aClearanceItem(5));
    clearanceItemRepository.decrementStock(ci.getId(), 1);

    Order order = orderWithStatus(OrderStatus.AWAITING_PAYMENT);
    order.addOrderItem(
        OrderItem.forDeal(
            order, ci, ci.getName(), ci.getRegularPrice(), null, 1, ci.getSalePrice()));
    orderRepository.save(order);

    // 만료 취소 + 재고 1개 복원 → remainingQuantity=5
    orderRepository.cancelIfAwaitingPayment(order.getId(), LocalDateTime.now());
    clearanceItemRepository.incrementStock(ci.getId(), 1);

    ClearanceItem restored = clearanceItemRepository.findById(ci.getId()).orElseThrow();
    assertThat(restored.getRemainingQuantity()).isEqualTo(5);
  }

  @Test
  void countOrdersByStatus_APPROVED환불_COMPLETED주문_제외() {
    // given — 같은 매장에 COMPLETED 3건: 환불 없음 / APPROVED 환불 / REQUESTED 환불, + CANCELLED 1건
    LocalDateTime start = LocalDateTime.now().minusDays(1);
    LocalDateTime end = LocalDateTime.now().plusDays(1);

    orderRepository.save(orderWithStatus(OrderStatus.COMPLETED)); // 카운트됨
    Order approvedRefunded = orderRepository.save(orderWithStatus(OrderStatus.COMPLETED));
    saveApprovedRefund(approvedRefunded); // 제외돼야 함
    Order requestedRefunded = orderRepository.save(orderWithStatus(OrderStatus.COMPLETED));
    saveRequestedRefund(requestedRefunded); // APPROVED 아님 → 카운트됨
    orderRepository.save(orderWithStatus(OrderStatus.CANCELLED)); // 환불 무관, 카운트됨

    // when
    List<Object[]> rows = orderRepository.countOrdersByStatus(store.getId(), start, end);

    // then — COMPLETED 는 APPROVED 환불 1건 제외하고 2건, CANCELLED 1건은 그대로
    assertThat(countOf(rows, OrderStatus.COMPLETED)).isEqualTo(2L);
    assertThat(countOf(rows, OrderStatus.CANCELLED)).isEqualTo(1L);
  }

  private long countOf(List<Object[]> rows, OrderStatus status) {
    return rows.stream()
        .filter(r -> r[0] == status)
        .mapToLong(r -> ((Number) r[1]).longValue())
        .sum();
  }

  private void saveApprovedRefund(Order order) {
    Refund refund =
        Refund.builder().order(order).reason("환불 사유").requestedAt(LocalDateTime.now()).build();
    refund.approve(LocalDateTime.now());
    refundRepository.save(refund);
  }

  private void saveRequestedRefund(Order order) {
    refundRepository.save(
        Refund.builder().order(order).reason("환불 사유").requestedAt(LocalDateTime.now()).build());
  }

  // ── helper ───────────────────────────────────────────────────────────────────

  private Order orderWithStatus(OrderStatus status) {
    return Order.builder()
        .customer(customer)
        .store(store)
        .status(status)
        .totalPrice(new BigDecimal("3000"))
        .pickupType(PickupType.ASAP)
        .pickupCode("1234")
        .normalTotal(new BigDecimal("3000"))
        .discountTotal(BigDecimal.ZERO)
        .finalAmount(new BigDecimal("3000"))
        .build();
  }

  private ClearanceItem aClearanceItem(int totalQuantity) {
    return ClearanceItem.builder()
        .store(store)
        .name("테스트떨이")
        .regularPrice(new BigDecimal("4500"))
        .salePrice(new BigDecimal("3000"))
        .totalQuantity(totalQuantity)
        .pickupStartAt(LocalDateTime.now().minusHours(1))
        .pickupEndAt(LocalDateTime.now().plusHours(3))
        .build();
  }
}
