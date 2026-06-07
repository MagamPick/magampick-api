package com.magampick.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

import com.magampick.clearance.exception.ClearanceItemErrorCode;
import com.magampick.clearance.repository.ClearanceItemRepository;
import com.magampick.customer.repository.CustomerRepository;
import com.magampick.global.exception.BusinessException;
import com.magampick.order.domain.Order;
import com.magampick.order.domain.PickupType;
import com.magampick.order.dto.CreateOrderRequest;
import com.magampick.order.dto.CreateOrderRequest.PickupRequest;
import com.magampick.order.dto.OrderResponse;
import com.magampick.order.exception.OrderErrorCode;
import com.magampick.order.fixture.OrderFixture;
import com.magampick.order.mapper.OrderMapper;
import com.magampick.order.repository.OrderRepository;
import com.magampick.payment.domain.PaymentStatus;
import com.magampick.payment.repository.PaymentRepository;
import com.magampick.payment.service.PaymentApproval;
import com.magampick.payment.service.PaymentGateway;
import com.magampick.product.exception.ProductErrorCode;
import com.magampick.product.repository.ProductRepository;
import com.magampick.store.domain.OperationStatus;
import com.magampick.store.domain.Store;
import com.magampick.store.domain.StoreBusinessHour;
import com.magampick.store.exception.StoreErrorCode;
import com.magampick.store.repository.StoreBusinessHourRepository;
import com.magampick.store.repository.StoreRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

  @Mock StoreRepository storeRepository;
  @Mock StoreBusinessHourRepository storeBusinessHourRepository;
  @Mock ClearanceItemRepository clearanceItemRepository;
  @Mock ProductRepository productRepository;
  @Mock CustomerRepository customerRepository;
  @Mock OrderRepository orderRepository;
  @Mock PaymentRepository paymentRepository;
  @Mock PaymentGateway paymentGateway;
  @Mock OrderMapper orderMapper;
  @Mock Clock clock;

  @InjectMocks OrderService orderService;

  /** 기본 Clock Mock 을 현재 KST 시각으로 설정 — 대부분의 테스트는 ASAP 픽업이므로 정확한 값 불필요. lenient() 로 미사용 경고 방지. */
  @BeforeEach
  void setUpClock() {
    lenient().when(clock.instant()).thenReturn(Instant.now());
    lenient().when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
  }

  /** 고정 Clock 으로 OrderService 를 직접 생성 — SLOT 픽업 시각 결정적 테스트용. */
  private OrderService newServiceWithClock(Clock fixedClock) {
    return new OrderService(
        storeRepository,
        storeBusinessHourRepository,
        clearanceItemRepository,
        productRepository,
        customerRepository,
        orderRepository,
        paymentRepository,
        paymentGateway,
        orderMapper,
        fixedClock);
  }

  private static final Long CUSTOMER_ID = 1L;
  private static final Long STORE_ID = 10L;
  private static final Long CI_ID = 100L;
  private static final Long PRODUCT_ID = 200L;

  private void givenStoreOpen() {
    Store store = OrderFixture.aStore(OperationStatus.OPEN);
    given(storeRepository.findById(STORE_ID)).willReturn(Optional.of(store));
    given(storeBusinessHourRepository.findByStoreIdAndDayOfWeek(anyLong(), any()))
        .willReturn(Optional.of(OrderFixture.aTodayBusinessHour(store)));
  }

  private void givenClearanceItem() {
    Store store = OrderFixture.aStore();
    given(clearanceItemRepository.findByIdWithStoreAndProduct(CI_ID))
        .willReturn(Optional.of(OrderFixture.aClearanceItem(store)));
  }

  /** DEAL 항목 재고 차감 성공 stub — 재고차감까지 흐르는 테스트에서만 호출. */
  private void givenDecrementStockSucceeds() {
    given(clearanceItemRepository.decrementStock(anyLong(), anyInt())).willReturn(1);
  }

  private void givenProduct() {
    Store store = OrderFixture.aStore();
    given(productRepository.findByIdAndDeletedAtIsNull(PRODUCT_ID))
        .willReturn(Optional.of(OrderFixture.aProduct(store)));
  }

  private void givenOrderSaved() {
    Store store = OrderFixture.aStore();
    Order order = OrderFixture.anOrder(OrderFixture.aCustomer(), store);
    given(orderRepository.save(any(Order.class))).willReturn(order);
  }

  private void givenPaymentApproved() {
    given(paymentGateway.approve(any()))
        .willReturn(
            new PaymentApproval("stub_key_abc", PaymentStatus.APPROVED, LocalDateTime.now()));
  }

  private void givenOrderMapperReturns() {
    given(orderMapper.toResponse(any(Order.class))).willReturn(OrderFixture.anOrderResponse(42L));
  }

  // ── 성공 케이스 ─────────────────────────────────────────────────────────────

  @Test
  void 주문_생성_성공() {
    // given
    givenStoreOpen();
    givenClearanceItem();
    givenDecrementStockSucceeds();
    givenOrderSaved();
    givenPaymentApproved();
    given(customerRepository.getReferenceById(CUSTOMER_ID)).willReturn(OrderFixture.aCustomer());
    given(paymentRepository.save(any())).willReturn(null);
    givenOrderMapperReturns();

    // when
    CreateOrderRequest req = OrderFixture.aDealOrderRequest(STORE_ID, CI_ID);
    OrderResponse response = orderService.createOrder(CUSTOMER_ID, req);

    // then
    assertThat(response).isNotNull();
    assertThat(response.status()).isEqualTo("PENDING");
    then(orderRepository).should().save(any(Order.class));
    then(paymentGateway).should().approve(any());
    then(paymentRepository).should().save(any());
  }

  @Test
  void 떨이_일반_혼합_성공() {
    // given
    givenStoreOpen();
    givenClearanceItem();
    givenDecrementStockSucceeds();
    givenProduct();
    givenOrderSaved();
    givenPaymentApproved();
    given(customerRepository.getReferenceById(CUSTOMER_ID)).willReturn(OrderFixture.aCustomer());
    given(paymentRepository.save(any())).willReturn(null);
    givenOrderMapperReturns();

    // when
    CreateOrderRequest req = OrderFixture.aMixedOrderRequest(STORE_ID, CI_ID, PRODUCT_ID);
    OrderResponse response = orderService.createOrder(CUSTOMER_ID, req);

    // then
    assertThat(response).isNotNull();
    // 재고 차감은 DEAL 항목(1개)만
    then(clearanceItemRepository).should().decrementStock(CI_ID, 1);
    then(productRepository).should().findByIdAndDeletedAtIsNull(PRODUCT_ID);
  }

  // ── 결제 동의 ────────────────────────────────────────────────────────────────

  @Test
  void 결제동의_누락_실패() {
    // given
    CreateOrderRequest req =
        OrderFixture.withPaymentAgreed(OrderFixture.aDealOrderRequest(STORE_ID, CI_ID), false);

    // when / then
    assertThatThrownBy(() -> orderService.createOrder(CUSTOMER_ID, req))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.PAYMENT_NOT_AGREED);

    then(storeRepository).should(never()).findById(any());
  }

  // ── 매장 영업 상태 ────────────────────────────────────────────────────────────

  @Test
  void 매장_영업중아님_실패() {
    // given
    Store closedStore = OrderFixture.aStore(OperationStatus.CLOSED_TODAY);
    given(storeRepository.findById(STORE_ID)).willReturn(Optional.of(closedStore));

    // when / then
    assertThatThrownBy(
            () ->
                orderService.createOrder(
                    CUSTOMER_ID, OrderFixture.aDealOrderRequest(STORE_ID, CI_ID)))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", StoreErrorCode.STORE_CLOSED);
  }

  @Test
  void 매장_영업일아님_실패() {
    // given
    Store store = OrderFixture.aStore(OperationStatus.OPEN);
    given(storeRepository.findById(STORE_ID)).willReturn(Optional.of(store));
    given(storeBusinessHourRepository.findByStoreIdAndDayOfWeek(anyLong(), any()))
        .willReturn(Optional.empty()); // 오늘 영업 요일 없음

    // when / then
    assertThatThrownBy(
            () ->
                orderService.createOrder(
                    CUSTOMER_ID, OrderFixture.aDealOrderRequest(STORE_ID, CI_ID)))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", StoreErrorCode.STORE_CLOSED);
  }

  // ── 떨이 상품 검증 ──────────────────────────────────────────────────────────

  @Test
  void 떨이_마감_실패() {
    // given
    givenStoreOpen();
    Store store = OrderFixture.aStore();
    given(clearanceItemRepository.findByIdWithStoreAndProduct(CI_ID))
        .willReturn(Optional.of(OrderFixture.aClosedClearanceItem(store)));

    // when / then
    assertThatThrownBy(
            () ->
                orderService.createOrder(
                    CUSTOMER_ID, OrderFixture.aDealOrderRequest(STORE_ID, CI_ID)))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", ClearanceItemErrorCode.CLEARANCE_CLOSED);

    then(clearanceItemRepository).should(never()).decrementStock(anyLong(), anyInt());
  }

  @Test
  void 떨이_픽업마감시간_초과_실패() {
    // given
    givenStoreOpen();
    Store store = OrderFixture.aStore();
    given(clearanceItemRepository.findByIdWithStoreAndProduct(CI_ID))
        .willReturn(Optional.of(OrderFixture.anExpiredClearanceItem(store)));

    // when / then
    assertThatThrownBy(
            () ->
                orderService.createOrder(
                    CUSTOMER_ID, OrderFixture.aDealOrderRequest(STORE_ID, CI_ID)))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", ClearanceItemErrorCode.CLEARANCE_CLOSED);
  }

  @Test
  void 떨이_재고부족_실패() {
    // given
    givenStoreOpen();
    givenClearanceItem();
    given(clearanceItemRepository.decrementStock(anyLong(), anyInt())).willReturn(0); // 재고 부족

    // when / then
    assertThatThrownBy(
            () ->
                orderService.createOrder(
                    CUSTOMER_ID, OrderFixture.aDealOrderRequest(STORE_ID, CI_ID)))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", ClearanceItemErrorCode.OUT_OF_STOCK);
  }

  // ── 일반 상품 검증 ──────────────────────────────────────────────────────────

  @Test
  void 일반상품_판매중지_실패() {
    // given
    givenStoreOpen();
    Store store = OrderFixture.aStore();
    given(productRepository.findByIdAndDeletedAtIsNull(PRODUCT_ID))
        .willReturn(Optional.of(OrderFixture.aSoldOutProduct(store)));

    // when / then
    assertThatThrownBy(
            () ->
                orderService.createOrder(
                    CUSTOMER_ID, OrderFixture.aMenuOrderRequest(STORE_ID, PRODUCT_ID)))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", ProductErrorCode.PRODUCT_NOT_ON_SALE);
  }

  // ── 다른 매장 혼합 ───────────────────────────────────────────────────────────

  @Test
  void 다른매장_혼합_실패() {
    // given
    givenStoreOpen();
    Store anotherStore = OrderFixture.aStore(OperationStatus.OPEN);
    ReflectionTestUtils.setField(anotherStore, "id", 999L); // 다른 매장 ID
    given(clearanceItemRepository.findByIdWithStoreAndProduct(CI_ID))
        .willReturn(Optional.of(OrderFixture.aClearanceItem(anotherStore)));

    // when / then
    assertThatThrownBy(
            () ->
                orderService.createOrder(
                    CUSTOMER_ID, OrderFixture.aDealOrderRequest(STORE_ID, CI_ID)))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.MIXED_STORE);
  }

  // ── 금액 교차검증 ────────────────────────────────────────────────────────────

  @Test
  void 금액_불일치_실패() {
    // given
    givenStoreOpen();
    givenClearanceItem();

    // 서버 계산: normalTotal=9000, discountTotal=3000, payTotal=6000 (qty=2)
    // 요청은 잘못된 금액
    CreateOrderRequest req =
        OrderFixture.withAmounts(
            OrderFixture.aDealOrderRequest(STORE_ID, CI_ID),
            new BigDecimal("5000"), // 잘못된 normalTotal
            new BigDecimal("1000"),
            new BigDecimal("4000"));

    // when / then
    assertThatThrownBy(() -> orderService.createOrder(CUSTOMER_ID, req))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.AMOUNT_MISMATCH);
  }

  // ── 픽업 시간 검증 ───────────────────────────────────────────────────────────

  @Test
  void 픽업시간_영업종료후_실패() {
    // given
    givenStoreOpen(); // closeTime = 21:00
    givenClearanceItem();

    // 21:00 이후 슬롯 → 거부
    CreateOrderRequest req =
        OrderFixture.withPickup(
            OrderFixture.aDealOrderRequest(STORE_ID, CI_ID),
            new PickupRequest(PickupType.SLOT, "21:00"));

    // when / then
    assertThatThrownBy(() -> orderService.createOrder(CUSTOMER_ID, req))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.INVALID_PICKUP_TIME);
  }

  @Test
  void 픽업시간_15분단위아님_실패() {
    // given
    givenStoreOpen();
    givenClearanceItem();

    CreateOrderRequest req =
        OrderFixture.withPickup(
            OrderFixture.aDealOrderRequest(STORE_ID, CI_ID),
            new PickupRequest(PickupType.SLOT, "18:10")); // 10분 단위 → 거부

    // when / then
    assertThatThrownBy(() -> orderService.createOrder(CUSTOMER_ID, req))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.INVALID_PICKUP_TIME);
  }

  @Test
  void 픽업시간_과거시각_실패() {
    // 고정 시각: 2026-06-07 18:00 KST → 슬롯 "09:15" 는 과거
    Clock fixedClock =
        Clock.fixed(
            LocalDate.of(2026, 6, 7).atTime(18, 0).toInstant(ZoneOffset.ofHours(9)),
            ZoneId.of("Asia/Seoul"));
    OrderService svc = newServiceWithClock(fixedClock);

    Store store = OrderFixture.aStore(OperationStatus.OPEN);
    given(storeRepository.findById(STORE_ID)).willReturn(Optional.of(store));
    given(storeBusinessHourRepository.findByStoreIdAndDayOfWeek(anyLong(), any()))
        .willReturn(Optional.of(OrderFixture.aTodayBusinessHour(store)));
    given(clearanceItemRepository.findByIdWithStoreAndProduct(CI_ID))
        .willReturn(Optional.of(OrderFixture.aClearanceItemNonExpiring(store)));

    CreateOrderRequest req =
        OrderFixture.withPickup(
            OrderFixture.aDealOrderRequest(STORE_ID, CI_ID),
            new PickupRequest(PickupType.SLOT, "09:15")); // 18:00 현재 기준 과거

    // when / then
    assertThatThrownBy(() -> svc.createOrder(CUSTOMER_ID, req))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.INVALID_PICKUP_TIME);
  }

  @Test
  void 픽업시간_영업개시전_실패() {
    // 고정 시각: 2026-06-07 08:00 KST → 슬롯 "09:15" 는 openTime(10:00) 전
    Clock fixedClock =
        Clock.fixed(
            LocalDate.of(2026, 6, 7).atTime(8, 0).toInstant(ZoneOffset.ofHours(9)),
            ZoneId.of("Asia/Seoul"));
    OrderService svc = newServiceWithClock(fixedClock);

    Store store = OrderFixture.aStore(OperationStatus.OPEN);
    given(storeRepository.findById(STORE_ID)).willReturn(Optional.of(store));
    // openTime = 10:00 (기본 09:00 아님)
    StoreBusinessHour hours =
        StoreBusinessHour.builder()
            .store(store)
            .dayOfWeek(DayOfWeek.SUNDAY)
            .openTime(LocalTime.of(10, 0))
            .closeTime(LocalTime.of(21, 0))
            .build();
    given(storeBusinessHourRepository.findByStoreIdAndDayOfWeek(anyLong(), any()))
        .willReturn(Optional.of(hours));
    given(clearanceItemRepository.findByIdWithStoreAndProduct(CI_ID))
        .willReturn(Optional.of(OrderFixture.aClearanceItemNonExpiring(store)));

    CreateOrderRequest req =
        OrderFixture.withPickup(
            OrderFixture.aDealOrderRequest(STORE_ID, CI_ID),
            new PickupRequest(PickupType.SLOT, "09:15")); // 09:15 < openTime(10:00)

    // when / then
    assertThatThrownBy(() -> svc.createOrder(CUSTOMER_ID, req))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.INVALID_PICKUP_TIME);
  }

  @Test
  void SLOT_픽업_성공() {
    // 고정 시각: 2026-06-07 12:00 KST → 슬롯 "18:30" 은 미래·15분·영업중
    Clock fixedClock =
        Clock.fixed(
            LocalDate.of(2026, 6, 7).atTime(12, 0).toInstant(ZoneOffset.ofHours(9)),
            ZoneId.of("Asia/Seoul"));
    OrderService svc = newServiceWithClock(fixedClock);

    Store store = OrderFixture.aStore(OperationStatus.OPEN);
    given(storeRepository.findById(STORE_ID)).willReturn(Optional.of(store));
    // openTime=09:00, closeTime=21:00
    given(storeBusinessHourRepository.findByStoreIdAndDayOfWeek(anyLong(), any()))
        .willReturn(Optional.of(OrderFixture.aTodayBusinessHour(store)));
    given(clearanceItemRepository.findByIdWithStoreAndProduct(CI_ID))
        .willReturn(Optional.of(OrderFixture.aClearanceItemNonExpiring(store)));
    givenDecrementStockSucceeds();
    given(customerRepository.getReferenceById(CUSTOMER_ID)).willReturn(OrderFixture.aCustomer());

    // Order 캡처해서 pickupTime 검증
    ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
    given(orderRepository.save(orderCaptor.capture()))
        .willReturn(OrderFixture.anOrder(OrderFixture.aCustomer(), store));
    givenPaymentApproved();
    given(paymentRepository.save(any())).willReturn(null);
    given(orderMapper.toResponse(any(Order.class))).willReturn(OrderFixture.anOrderResponse(42L));

    CreateOrderRequest req =
        OrderFixture.withPickup(
            OrderFixture.aDealOrderRequest(STORE_ID, CI_ID),
            new PickupRequest(PickupType.SLOT, "18:30"));

    // when
    OrderResponse response = svc.createOrder(CUSTOMER_ID, req);

    // then — 성공 및 pickupTime = 2026-06-07T18:30 확인
    assertThat(response).isNotNull();
    assertThat(orderCaptor.getValue().getPickupTime().toLocalTime())
        .isEqualTo(LocalTime.of(18, 30));
    assertThat(orderCaptor.getValue().getPickupTime().toLocalDate())
        .isEqualTo(LocalDate.of(2026, 6, 7));
  }

  // ── 재고 차감 + 픽업코드 + 결제 ───────────────────────────────────────────────

  @Test
  void 재고차감_검증() {
    // given
    givenStoreOpen();
    givenClearanceItem();
    givenDecrementStockSucceeds();
    givenOrderSaved();
    givenPaymentApproved();
    given(customerRepository.getReferenceById(CUSTOMER_ID)).willReturn(OrderFixture.aCustomer());
    given(paymentRepository.save(any())).willReturn(null);
    givenOrderMapperReturns();

    // when — qty=2
    orderService.createOrder(CUSTOMER_ID, OrderFixture.aDealOrderRequest(STORE_ID, CI_ID));

    // then — decrementStock 호출 확인 (qty=2)
    then(clearanceItemRepository).should().decrementStock(CI_ID, 2);
  }

  @Test
  void 픽업코드_4자리_발급() {
    // given
    givenStoreOpen();
    givenClearanceItem();
    givenDecrementStockSucceeds();
    Store store = OrderFixture.aStore();
    Order order = OrderFixture.anOrder(OrderFixture.aCustomer(), store);
    given(orderRepository.save(any(Order.class))).willReturn(order);
    givenPaymentApproved();
    given(customerRepository.getReferenceById(CUSTOMER_ID)).willReturn(OrderFixture.aCustomer());
    given(paymentRepository.save(any())).willReturn(null);
    given(orderMapper.toResponse(any(Order.class))).willReturn(OrderFixture.anOrderResponse(42L));

    // when
    CreateOrderRequest req = OrderFixture.aDealOrderRequest(STORE_ID, CI_ID);
    OrderResponse response = orderService.createOrder(CUSTOMER_ID, req);

    // then — Order 에 pickupCode 4자리 세팅 확인 (OrderFixture.anOrder 에 "3827" 세팅됨)
    assertThat(response.pickupCode()).matches("\\d{4}");
  }

  @Test
  void 결제_승인_후_PENDING_확정() {
    // given
    givenStoreOpen();
    givenClearanceItem();
    givenDecrementStockSucceeds();
    givenOrderSaved();
    givenPaymentApproved();
    given(customerRepository.getReferenceById(CUSTOMER_ID)).willReturn(OrderFixture.aCustomer());
    given(paymentRepository.save(any())).willReturn(null);
    given(orderMapper.toResponse(any(Order.class))).willReturn(OrderFixture.anOrderResponse(42L));

    // when
    OrderResponse response =
        orderService.createOrder(CUSTOMER_ID, OrderFixture.aDealOrderRequest(STORE_ID, CI_ID));

    // then
    assertThat(response.status()).isEqualTo("PENDING");
    then(paymentRepository).should().save(any());
  }
}
