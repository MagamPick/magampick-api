package com.magampick.order.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.magampick.clearance.domain.ClearanceItem;
import com.magampick.clearance.repository.ClearanceItemRepository;
import com.magampick.customer.domain.Customer;
import com.magampick.customer.repository.CustomerRepository;
import com.magampick.order.domain.Order;
import com.magampick.order.domain.OrderStatus;
import com.magampick.order.dto.DevSeedOrderRequest;
import com.magampick.order.fixture.OrderFixture;
import com.magampick.order.repository.OrderRepository;
import com.magampick.payment.repository.PaymentRepository;
import com.magampick.point.service.PointService;
import com.magampick.store.domain.Store;
import com.magampick.store.repository.StoreRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DevOrderSeedServiceTest {

  @Mock CustomerRepository customerRepository;
  @Mock StoreRepository storeRepository;
  @Mock ClearanceItemRepository clearanceItemRepository;
  @Mock OrderRepository orderRepository;
  @Mock PaymentRepository paymentRepository;
  @Mock PointService pointService;
  @Mock Clock clock;

  @InjectMocks DevOrderSeedService service;

  private Customer customer;
  private Store store;
  private ClearanceItem clearanceItem;

  @BeforeEach
  void setUp() {
    given(clock.instant()).willReturn(Instant.parse("2026-06-15T00:00:00Z"));
    given(clock.getZone()).willReturn(ZoneId.of("Asia/Seoul"));

    customer = OrderFixture.aCustomer();
    store = OrderFixture.aStore();
    clearanceItem = OrderFixture.aClearanceItemNonExpiring(store);

    given(customerRepository.findById(1L)).willReturn(Optional.of(customer));
    given(storeRepository.findByIdAndDeletedAtIsNull(10L)).willReturn(Optional.of(store));
    given(clearanceItemRepository.findByIdWithStoreAndProduct(100L))
        .willReturn(Optional.of(clearanceItem));
    given(clearanceItemRepository.decrementStock(eq(100L), eq(1))).willReturn(1);
    given(customerRepository.getReferenceById(1L)).willReturn(customer);
    given(storeRepository.getReferenceById(10L)).willReturn(store);
    given(clearanceItemRepository.getReferenceById(100L)).willReturn(clearanceItem);
    given(paymentRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
  }

  @Test
  void COMPLETED_시드_포인트적립_호출() {
    // given — salePrice=3000, earnedPoints = 3000/100 = 30
    Order savedOrder = orderWithId(1L, new BigDecimal("3000"), null);
    given(orderRepository.save(any())).willReturn(savedOrder);

    DevSeedOrderRequest req = new DevSeedOrderRequest(OrderStatus.COMPLETED, 1L, 10L, 100L);

    // when
    service.seedOrder(req);

    // then — pointService.earn() 호출됨 (amount=30)
    then(pointService).should().earn(any(Order.class), eq(30L));
  }

  @Test
  void PENDING_시드_포인트적립_미호출() {
    // given
    Order savedOrder = orderWithId(1L, new BigDecimal("3000"), null);
    given(orderRepository.save(any())).willReturn(savedOrder);

    DevSeedOrderRequest req = new DevSeedOrderRequest(OrderStatus.PENDING, 1L, 10L, 100L);

    // when
    service.seedOrder(req);

    // then
    then(pointService).should(never()).earn(any(), anyLong());
  }

  @Test
  void COMPLETED_시드_salePrice가_100미만이면_적립미호출() {
    // given — salePrice=50, earnedPoints = 50/100 = 0
    Order savedOrder = orderWithId(1L, new BigDecimal("50"), null);
    given(orderRepository.save(any())).willReturn(savedOrder);

    DevSeedOrderRequest req = new DevSeedOrderRequest(OrderStatus.COMPLETED, 1L, 10L, 100L);

    // when
    service.seedOrder(req);

    // then
    then(pointService).should(never()).earn(any(), anyLong());
  }

  // ── 헬퍼 ─────────────────────────────────────────────────────────────────────

  private Order orderWithId(Long id, BigDecimal salePrice, Long earnedPoints) {
    long ep = salePrice.longValue() / 100;
    Order order =
        Order.builder()
            .customer(customer)
            .store(store)
            .status(OrderStatus.PENDING)
            .totalPrice(salePrice)
            .pickupCode("1234")
            .normalTotal(salePrice)
            .discountTotal(BigDecimal.ZERO)
            .finalAmount(salePrice)
            .earnedPoints(ep > 0 ? ep : null)
            .build();
    ReflectionTestUtils.setField(order, "id", id);
    return order;
  }
}
