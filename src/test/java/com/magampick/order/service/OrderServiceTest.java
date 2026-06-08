package com.magampick.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.magampick.clearance.exception.ClearanceItemErrorCode;
import com.magampick.clearance.repository.ClearanceItemRepository;
import com.magampick.coupon.domain.Coupon;
import com.magampick.coupon.domain.CouponDiscountType;
import com.magampick.coupon.domain.CouponKind;
import com.magampick.coupon.domain.UserCoupon;
import com.magampick.coupon.exception.CouponErrorCode;
import com.magampick.coupon.service.CouponService;
import com.magampick.customer.repository.CustomerRepository;
import com.magampick.global.exception.BusinessException;
import com.magampick.order.domain.ItemKind;
import com.magampick.order.domain.Order;
import com.magampick.order.domain.OrderStatus;
import com.magampick.order.domain.PickupType;
import com.magampick.order.dto.CreateOrderRequest;
import com.magampick.order.dto.CreateOrderRequest.PickupRequest;
import com.magampick.order.dto.OrderResponse;
import com.magampick.order.dto.PrepareOrderResponse;
import com.magampick.order.dto.SellerOrderResponse;
import com.magampick.order.exception.OrderErrorCode;
import com.magampick.order.fixture.OrderFixture;
import com.magampick.order.mapper.OrderMapper;
import com.magampick.order.repository.OrderRepository;
import com.magampick.payment.domain.PaymentStatus;
import com.magampick.payment.repository.PaymentRepository;
import com.magampick.payment.service.PaymentApproval;
import com.magampick.payment.service.PaymentCancellation;
import com.magampick.payment.service.PaymentGateway;
import com.magampick.point.dto.PointSummaryResponse;
import com.magampick.point.service.PointService;
import com.magampick.product.exception.ProductErrorCode;
import com.magampick.product.repository.ProductRepository;
import com.magampick.refund.mapper.RefundMapper;
import com.magampick.refund.repository.RefundRepository;
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
import java.util.List;
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
  @Mock RefundRepository refundRepository;
  @Mock RefundMapper refundMapper;
  @Mock Clock clock;
  @Mock CouponService couponService;
  @Mock PointService pointService;

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
        refundRepository,
        refundMapper,
        couponService,
        pointService,
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

  private void givenPaymentCancelled() {
    given(paymentGateway.cancel(any()))
        .willReturn(
            new PaymentCancellation("stub_key_abc", PaymentStatus.CANCELED, LocalDateTime.now()));
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
    given(customerRepository.getReferenceById(CUSTOMER_ID)).willReturn(OrderFixture.aCustomer());

    // when
    CreateOrderRequest req = OrderFixture.aDealOrderRequest(STORE_ID, CI_ID);
    PrepareOrderResponse response = orderService.createOrder(CUSTOMER_ID, req);

    // then
    assertThat(response).isNotNull();
    assertThat(response.amount())
        .isEqualByComparingTo(new BigDecimal("6000")); // 4500*2 - 3000 = 6000
    then(orderRepository).should().save(any(Order.class));
    then(paymentGateway).should(never()).approve(any()); // createOrder 는 결제 호출 안 함
    then(paymentRepository).should(never()).save(any());
  }

  @Test
  void 떨이_일반_혼합_성공() {
    // given
    givenStoreOpen();
    givenClearanceItem();
    givenDecrementStockSucceeds();
    givenProduct();
    givenOrderSaved();
    given(customerRepository.getReferenceById(CUSTOMER_ID)).willReturn(OrderFixture.aCustomer());

    // when
    CreateOrderRequest req = OrderFixture.aMixedOrderRequest(STORE_ID, CI_ID, PRODUCT_ID);
    PrepareOrderResponse response = orderService.createOrder(CUSTOMER_ID, req);

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
    given(customerRepository.getReferenceById(CUSTOMER_ID)).willReturn(OrderFixture.aCustomer());

    CreateOrderRequest req =
        OrderFixture.withPickup(
            OrderFixture.aDealOrderRequest(STORE_ID, CI_ID),
            new PickupRequest(PickupType.SLOT, "18:30"));

    // when
    PrepareOrderResponse response = svc.createOrder(CUSTOMER_ID, req);

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
    given(customerRepository.getReferenceById(CUSTOMER_ID)).willReturn(OrderFixture.aCustomer());

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
    given(customerRepository.getReferenceById(CUSTOMER_ID)).willReturn(OrderFixture.aCustomer());
    ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
    given(orderRepository.save(captor.capture()))
        .willReturn(OrderFixture.anOrder(OrderFixture.aCustomer(), OrderFixture.aStore()));

    // when
    orderService.createOrder(CUSTOMER_ID, OrderFixture.aDealOrderRequest(STORE_ID, CI_ID));

    // then — 저장된 Order 에 pickupCode 4자리 세팅 확인
    assertThat(captor.getValue().getPickupCode()).matches("\\d{4}");
  }

  // ── listMyOrders ─────────────────────────────────────────────────────────────

  @Test
  void 본인_주문_목록_조회_성공() {
    // given
    Store store = OrderFixture.aStore();
    Order order = OrderFixture.anOrder(OrderFixture.aCustomer(), store);
    given(orderRepository.findByCustomerIdAndStatusInOrderByCreatedAtDesc(eq(CUSTOMER_ID), any()))
        .willReturn(List.of(order));
    given(orderMapper.toResponse(order)).willReturn(OrderFixture.anOrderResponse(42L));

    // when
    List<OrderResponse> result = orderService.listMyOrders(CUSTOMER_ID, "ALL");

    // then
    assertThat(result).hasSize(1);
    assertThat(result.get(0).status()).isEqualTo("PENDING");
  }

  @Test
  void 소비자_목록_segment_PICKUP_WAITING_필터() {
    // given
    given(orderRepository.findByCustomerIdAndStatusInOrderByCreatedAtDesc(eq(CUSTOMER_ID), any()))
        .willReturn(List.of());

    // when
    List<OrderResponse> result = orderService.listMyOrders(CUSTOMER_ID, "PICKUP_WAITING");

    // then — PENDING/PREPARING/READY 3개 statuses 로 조회
    assertThat(result).isEmpty();
    ArgumentCaptor<List<OrderStatus>> captor = ArgumentCaptor.forClass(List.class);
    then(orderRepository)
        .should()
        .findByCustomerIdAndStatusInOrderByCreatedAtDesc(eq(CUSTOMER_ID), captor.capture());
    assertThat(captor.getValue())
        .containsExactlyInAnyOrder(OrderStatus.PENDING, OrderStatus.PREPARING, OrderStatus.READY);
  }

  @Test
  void 소비자_목록_segment_DONE_필터() {
    // given
    given(orderRepository.findByCustomerIdAndStatusInOrderByCreatedAtDesc(eq(CUSTOMER_ID), any()))
        .willReturn(List.of());

    // when
    orderService.listMyOrders(CUSTOMER_ID, "DONE");

    // then — COMPLETED/CANCELLED/REJECTED/NO_SHOW 4개 statuses 로 조회
    ArgumentCaptor<List<OrderStatus>> captor = ArgumentCaptor.forClass(List.class);
    then(orderRepository)
        .should()
        .findByCustomerIdAndStatusInOrderByCreatedAtDesc(eq(CUSTOMER_ID), captor.capture());
    assertThat(captor.getValue())
        .containsExactlyInAnyOrder(
            OrderStatus.COMPLETED,
            OrderStatus.CANCELLED,
            OrderStatus.REJECTED,
            OrderStatus.NO_SHOW);
  }

  // ── getMyOrder ────────────────────────────────────────────────────────────────

  @Test
  void 본인_주문_상세_조회_성공() {
    // given
    Store store = OrderFixture.aStore();
    Order order = OrderFixture.anOrder(OrderFixture.aCustomer(), store);
    ReflectionTestUtils.setField(order, "id", 42L);
    given(orderRepository.findById(42L)).willReturn(Optional.of(order));
    given(orderMapper.toResponse(order)).willReturn(OrderFixture.anOrderResponse(42L));

    // when
    OrderResponse result = orderService.getMyOrder(CUSTOMER_ID, 42L);

    // then
    assertThat(result.id()).isEqualTo(42L);
  }

  @Test
  void 주문_없음_404() {
    // given
    given(orderRepository.findById(99L)).willReturn(Optional.empty());

    // when / then
    assertThatThrownBy(() -> orderService.getMyOrder(CUSTOMER_ID, 99L))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.ORDER_NOT_FOUND);
  }

  @Test
  void 타인_주문_조회시_403() {
    // given — customerId=999 인 다른 고객 주문
    Store store = OrderFixture.aStore();
    Order order = OrderFixture.anOrder(OrderFixture.aCustomer(), store); // customer.id=1
    ReflectionTestUtils.setField(order, "id", 42L);
    given(orderRepository.findById(42L)).willReturn(Optional.of(order));

    // when / then — customerId=999 (≠1) 접근 시 403
    assertThatThrownBy(() -> orderService.getMyOrder(999L, 42L))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.ORDER_FORBIDDEN);
  }

  // ── listStoreOrders ───────────────────────────────────────────────────────────

  @Test
  void 사장_매장_주문_목록_조회_성공() {
    // given
    Store store = OrderFixture.aStore(); // seller.id=2, store.id=10
    given(storeRepository.findByIdAndSellerId(STORE_ID, 2L)).willReturn(Optional.of(store));
    Order order = OrderFixture.anOrder(OrderFixture.aCustomer(), store);
    given(orderRepository.findByStoreIdAndStatusInOrderByCreatedAtDesc(eq(STORE_ID), any()))
        .willReturn(List.of(order));
    given(orderMapper.toSellerResponse(order)).willReturn(OrderFixture.aSellerOrderResponse(42L));

    // when
    List<SellerOrderResponse> result = orderService.listStoreOrders(2L, STORE_ID, "ALL");

    // then
    assertThat(result).hasSize(1);
  }

  @Test
  void 사장_다른매장_접근시_403() {
    // given — storeId=10 은 sellerId=2 소유, 접근자=sellerId=999
    given(storeRepository.findByIdAndSellerId(STORE_ID, 999L)).willReturn(Optional.empty());

    // when / then
    assertThatThrownBy(() -> orderService.listStoreOrders(999L, STORE_ID, "ALL"))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", StoreErrorCode.STORE_ACCESS_DENIED);
  }

  @Test
  void 사장_목록_segment_PENDING_필터() {
    // given
    Store store = OrderFixture.aStore();
    given(storeRepository.findByIdAndSellerId(STORE_ID, 2L)).willReturn(Optional.of(store));
    given(orderRepository.findByStoreIdAndStatusInOrderByCreatedAtDesc(eq(STORE_ID), any()))
        .willReturn(List.of());

    // when
    orderService.listStoreOrders(2L, STORE_ID, "PENDING");

    // then — PENDING 1개만
    ArgumentCaptor<List<OrderStatus>> captor = ArgumentCaptor.forClass(List.class);
    then(orderRepository)
        .should()
        .findByStoreIdAndStatusInOrderByCreatedAtDesc(eq(STORE_ID), captor.capture());
    assertThat(captor.getValue()).containsExactly(OrderStatus.PENDING);
  }

  @Test
  void 사장_목록_segment_CANCELLED_필터() {
    // given
    Store store = OrderFixture.aStore();
    given(storeRepository.findByIdAndSellerId(STORE_ID, 2L)).willReturn(Optional.of(store));
    given(orderRepository.findByStoreIdAndStatusInOrderByCreatedAtDesc(eq(STORE_ID), any()))
        .willReturn(List.of());

    // when
    orderService.listStoreOrders(2L, STORE_ID, "CANCELLED");

    // then — CANCELLED/REJECTED/NO_SHOW 3개
    ArgumentCaptor<List<OrderStatus>> captor = ArgumentCaptor.forClass(List.class);
    then(orderRepository)
        .should()
        .findByStoreIdAndStatusInOrderByCreatedAtDesc(eq(STORE_ID), captor.capture());
    assertThat(captor.getValue())
        .containsExactlyInAnyOrder(
            OrderStatus.CANCELLED, OrderStatus.REJECTED, OrderStatus.NO_SHOW);
  }

  // ── getStoreOrder ─────────────────────────────────────────────────────────────

  @Test
  void 사장_주문_상세_조회_성공() {
    // given
    Store store = OrderFixture.aStore(); // seller.id=2, store.id=10
    Order order = OrderFixture.anOrder(OrderFixture.aCustomer(), store);
    ReflectionTestUtils.setField(order, "id", 42L);
    given(orderRepository.findById(42L)).willReturn(Optional.of(order));
    given(orderMapper.toSellerResponse(order)).willReturn(OrderFixture.aSellerOrderResponse(42L));

    // when
    SellerOrderResponse result = orderService.getStoreOrder(2L, 42L);

    // then
    assertThat(result.id()).isEqualTo(42L);
  }

  @Test
  void 사장_본인_매장_아닌_주문_조회시_403() {
    // given — 주문의 store.seller.id=2, 접근자=sellerId=999
    Store store = OrderFixture.aStore(); // seller.id=2
    Order order = OrderFixture.anOrder(OrderFixture.aCustomer(), store);
    ReflectionTestUtils.setField(order, "id", 42L);
    given(orderRepository.findById(42L)).willReturn(Optional.of(order));

    // when / then
    assertThatThrownBy(() -> orderService.getStoreOrder(999L, 42L))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.ORDER_FORBIDDEN);
  }

  @Test
  void 사장_주문_없음_404() {
    // given
    given(orderRepository.findById(99L)).willReturn(Optional.empty());

    // when / then
    assertThatThrownBy(() -> orderService.getStoreOrder(2L, 99L))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.ORDER_NOT_FOUND);
  }

  // ── cancelOrder ─────────────────────────────────────────────────────────────

  @Test
  void 주문접수_상태에서_취소_성공() {
    // given — PENDING 주문, customer.id=1
    Store store = OrderFixture.aStore();
    Order order = OrderFixture.anOrder(OrderFixture.aCustomer(), store); // PENDING
    ReflectionTestUtils.setField(order, "id", 42L);
    given(orderRepository.findById(42L)).willReturn(Optional.of(order));
    ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
    given(orderRepository.save(captor.capture())).willReturn(order);
    given(orderMapper.toResponse(any(Order.class))).willReturn(OrderFixture.anOrderResponse(42L));
    given(paymentRepository.findByOrderId(42L)).willReturn(Optional.empty()); // 결제 없음 (stub 시나리오)

    // when
    OrderResponse result = orderService.cancelOrder(CUSTOMER_ID, 42L);

    // then
    assertThat(result).isNotNull();
    assertThat(captor.getValue().getStatus()).isEqualTo(OrderStatus.CANCELLED);
    assertThat(captor.getValue().getCancelledAt()).isNotNull();
    then(orderRepository).should().save(any(Order.class));
  }

  @Test
  void 취소_토스_환불_호출() {
    // given — PENDING 주문 + Payment 있음
    Store store = OrderFixture.aStore();
    Order order = OrderFixture.anOrder(OrderFixture.aCustomer(), store);
    ReflectionTestUtils.setField(order, "id", 42L);
    given(orderRepository.findById(42L)).willReturn(Optional.of(order));
    given(orderRepository.save(any(Order.class))).willReturn(order);
    given(orderMapper.toResponse(any(Order.class))).willReturn(OrderFixture.anOrderResponse(42L));

    com.magampick.payment.domain.Payment payment =
        com.magampick.payment.domain.Payment.builder()
            .order(order)
            .provider("TOSS")
            .method("toss")
            .paymentKey("toss_pk")
            .amount(new BigDecimal("6000"))
            .status(PaymentStatus.APPROVED)
            .approvedAt(LocalDateTime.now())
            .build();
    given(paymentRepository.findByOrderId(42L)).willReturn(Optional.of(payment));
    givenPaymentCancelled();
    given(paymentRepository.save(any())).willReturn(null);

    // when
    orderService.cancelOrder(CUSTOMER_ID, 42L);

    // then
    then(paymentGateway).should().cancel(any());
    then(paymentRepository).should().save(any());
  }

  @Test
  void 준비중_상태에서_취소시_409() {
    // given — PREPARING 주문 → CANCELLED 불가
    Store store = OrderFixture.aStore();
    Order order =
        OrderFixture.anOrderWithStatus(OrderFixture.aCustomer(), store, OrderStatus.PREPARING);
    ReflectionTestUtils.setField(order, "id", 42L);
    given(orderRepository.findById(42L)).willReturn(Optional.of(order));

    // when / then
    assertThatThrownBy(() -> orderService.cancelOrder(CUSTOMER_ID, 42L))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.INVALID_ORDER_TRANSITION);
    then(orderRepository).should(never()).save(any());
  }

  @Test
  void 취소_주문없음_404() {
    given(orderRepository.findById(99L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> orderService.cancelOrder(CUSTOMER_ID, 99L))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.ORDER_NOT_FOUND);
  }

  @Test
  void 취소_타인주문_403() {
    // given — customer.id=1 의 주문, 접근자=999
    Store store = OrderFixture.aStore();
    Order order = OrderFixture.anOrder(OrderFixture.aCustomer(), store);
    ReflectionTestUtils.setField(order, "id", 42L);
    given(orderRepository.findById(42L)).willReturn(Optional.of(order));

    assertThatThrownBy(() -> orderService.cancelOrder(999L, 42L))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.ORDER_FORBIDDEN);
  }

  // ── acceptOrder ──────────────────────────────────────────────────────────────

  @Test
  void 사장_수락_성공() {
    // given — PENDING 주문, store.seller.id=2
    Store store = OrderFixture.aStore();
    Order order = OrderFixture.anOrder(OrderFixture.aCustomer(), store); // PENDING
    ReflectionTestUtils.setField(order, "id", 42L);
    given(orderRepository.findById(42L)).willReturn(Optional.of(order));
    ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
    given(orderRepository.save(captor.capture())).willReturn(order);
    given(orderMapper.toSellerResponse(any(Order.class)))
        .willReturn(OrderFixture.aSellerOrderResponse(42L));

    // when
    SellerOrderResponse result = orderService.acceptOrder(2L, 42L);

    // then
    assertThat(result).isNotNull();
    assertThat(captor.getValue().getStatus()).isEqualTo(OrderStatus.PREPARING);
    assertThat(captor.getValue().getAcceptedAt()).isNotNull();
  }

  @Test
  void 비접수중_상태에서_수락시_409() {
    // given — PREPARING 주문 → PENDING 이 아니므로 수락 불가
    Store store = OrderFixture.aStore();
    Order order =
        OrderFixture.anOrderWithStatus(OrderFixture.aCustomer(), store, OrderStatus.PREPARING);
    ReflectionTestUtils.setField(order, "id", 42L);
    given(orderRepository.findById(42L)).willReturn(Optional.of(order));

    assertThatThrownBy(() -> orderService.acceptOrder(2L, 42L))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.INVALID_ORDER_TRANSITION);
    then(orderRepository).should(never()).save(any());
  }

  @Test
  void 사장_수락_주문없음_404() {
    given(orderRepository.findById(99L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> orderService.acceptOrder(2L, 99L))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.ORDER_NOT_FOUND);
  }

  @Test
  void 사장_수락_타매장_403() {
    // given — store.seller.id=2, 접근자=sellerId=999
    Store store = OrderFixture.aStore();
    Order order = OrderFixture.anOrder(OrderFixture.aCustomer(), store);
    ReflectionTestUtils.setField(order, "id", 42L);
    given(orderRepository.findById(42L)).willReturn(Optional.of(order));

    assertThatThrownBy(() -> orderService.acceptOrder(999L, 42L))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.ORDER_FORBIDDEN);
  }

  // ── rejectOrder ──────────────────────────────────────────────────────────────

  @Test
  void 사장_거절_성공() {
    // given — PENDING 주문
    Store store = OrderFixture.aStore();
    Order order = OrderFixture.anOrder(OrderFixture.aCustomer(), store);
    ReflectionTestUtils.setField(order, "id", 42L);
    given(orderRepository.findById(42L)).willReturn(Optional.of(order));
    ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
    given(orderRepository.save(captor.capture())).willReturn(order);
    given(orderMapper.toSellerResponse(any(Order.class)))
        .willReturn(OrderFixture.aSellerOrderResponse(42L));
    given(paymentRepository.findByOrderId(42L)).willReturn(Optional.empty());

    // when
    SellerOrderResponse result = orderService.rejectOrder(2L, 42L);

    // then
    assertThat(result).isNotNull();
    assertThat(captor.getValue().getStatus()).isEqualTo(OrderStatus.REJECTED);
    assertThat(captor.getValue().getRejectedAt()).isNotNull();
  }

  @Test
  void 비접수중_상태에서_거절시_409() {
    Store store = OrderFixture.aStore();
    Order order =
        OrderFixture.anOrderWithStatus(OrderFixture.aCustomer(), store, OrderStatus.PREPARING);
    ReflectionTestUtils.setField(order, "id", 42L);
    given(orderRepository.findById(42L)).willReturn(Optional.of(order));

    assertThatThrownBy(() -> orderService.rejectOrder(2L, 42L))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.INVALID_ORDER_TRANSITION);
    then(orderRepository).should(never()).save(any());
  }

  @Test
  void 사장_거절_주문없음_404() {
    given(orderRepository.findById(99L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> orderService.rejectOrder(2L, 99L))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.ORDER_NOT_FOUND);
  }

  @Test
  void 사장_거절_타매장_403() {
    Store store = OrderFixture.aStore();
    Order order = OrderFixture.anOrder(OrderFixture.aCustomer(), store);
    ReflectionTestUtils.setField(order, "id", 42L);
    given(orderRepository.findById(42L)).willReturn(Optional.of(order));

    assertThatThrownBy(() -> orderService.rejectOrder(999L, 42L))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.ORDER_FORBIDDEN);
  }

  // ── readyOrder ───────────────────────────────────────────────────────────────

  @Test
  void 사장_준비완료_성공() {
    // given — PREPARING 주문
    Store store = OrderFixture.aStore();
    Order order =
        OrderFixture.anOrderWithStatus(OrderFixture.aCustomer(), store, OrderStatus.PREPARING);
    ReflectionTestUtils.setField(order, "id", 42L);
    given(orderRepository.findById(42L)).willReturn(Optional.of(order));
    ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
    given(orderRepository.save(captor.capture())).willReturn(order);
    given(orderMapper.toSellerResponse(any(Order.class)))
        .willReturn(OrderFixture.aSellerOrderResponse(42L));

    // when
    SellerOrderResponse result = orderService.readyOrder(2L, 42L);

    // then
    assertThat(result).isNotNull();
    assertThat(captor.getValue().getStatus()).isEqualTo(OrderStatus.READY);
    assertThat(captor.getValue().getReadyAt()).isNotNull();
  }

  @Test
  void 접수중_상태에서_준비완료시_409() {
    // given — PENDING 주문 → PREPARING 이 아니므로 준비완료 불가
    Store store = OrderFixture.aStore();
    Order order = OrderFixture.anOrder(OrderFixture.aCustomer(), store); // PENDING
    ReflectionTestUtils.setField(order, "id", 42L);
    given(orderRepository.findById(42L)).willReturn(Optional.of(order));

    assertThatThrownBy(() -> orderService.readyOrder(2L, 42L))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.INVALID_ORDER_TRANSITION);
    then(orderRepository).should(never()).save(any());
  }

  @Test
  void 사장_준비완료_주문없음_404() {
    given(orderRepository.findById(99L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> orderService.readyOrder(2L, 99L))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.ORDER_NOT_FOUND);
  }

  @Test
  void 사장_준비완료_타매장_403() {
    Store store = OrderFixture.aStore();
    Order order =
        OrderFixture.anOrderWithStatus(OrderFixture.aCustomer(), store, OrderStatus.PREPARING);
    ReflectionTestUtils.setField(order, "id", 42L);
    given(orderRepository.findById(42L)).willReturn(Optional.of(order));

    assertThatThrownBy(() -> orderService.readyOrder(999L, 42L))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.ORDER_FORBIDDEN);
  }

  // ── completeOrder ────────────────────────────────────────────────────────────

  @Test
  void 사장_수령완료_성공() {
    // given — READY 주문
    Store store = OrderFixture.aStore();
    Order order =
        OrderFixture.anOrderWithStatus(OrderFixture.aCustomer(), store, OrderStatus.READY);
    ReflectionTestUtils.setField(order, "id", 42L);
    given(orderRepository.findById(42L)).willReturn(Optional.of(order));
    ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
    given(orderRepository.save(captor.capture())).willReturn(order);
    given(orderMapper.toSellerResponse(any(Order.class)))
        .willReturn(OrderFixture.aSellerOrderResponse(42L));

    // when
    SellerOrderResponse result = orderService.completeOrder(2L, 42L);

    // then
    assertThat(result).isNotNull();
    assertThat(captor.getValue().getStatus()).isEqualTo(OrderStatus.COMPLETED);
    assertThat(captor.getValue().getCompletedAt()).isNotNull();
  }

  @Test
  void 준비중_상태에서_수령완료시_409() {
    // given — PREPARING 주문 → READY 가 아니므로 수령완료 불가
    Store store = OrderFixture.aStore();
    Order order =
        OrderFixture.anOrderWithStatus(OrderFixture.aCustomer(), store, OrderStatus.PREPARING);
    ReflectionTestUtils.setField(order, "id", 42L);
    given(orderRepository.findById(42L)).willReturn(Optional.of(order));

    assertThatThrownBy(() -> orderService.completeOrder(2L, 42L))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.INVALID_ORDER_TRANSITION);
    then(orderRepository).should(never()).save(any());
  }

  @Test
  void 사장_수령완료_주문없음_404() {
    given(orderRepository.findById(99L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> orderService.completeOrder(2L, 99L))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.ORDER_NOT_FOUND);
  }

  @Test
  void 사장_수령완료_타매장_403() {
    Store store = OrderFixture.aStore();
    Order order =
        OrderFixture.anOrderWithStatus(OrderFixture.aCustomer(), store, OrderStatus.READY);
    ReflectionTestUtils.setField(order, "id", 42L);
    given(orderRepository.findById(42L)).willReturn(Optional.of(order));

    assertThatThrownBy(() -> orderService.completeOrder(999L, 42L))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.ORDER_FORBIDDEN);
  }

  // ── noShowOrder ──────────────────────────────────────────────────────────────

  @Test
  void 사장_미수령_성공() {
    // given — READY 주문
    Store store = OrderFixture.aStore();
    Order order =
        OrderFixture.anOrderWithStatus(OrderFixture.aCustomer(), store, OrderStatus.READY);
    ReflectionTestUtils.setField(order, "id", 42L);
    given(orderRepository.findById(42L)).willReturn(Optional.of(order));
    ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
    given(orderRepository.save(captor.capture())).willReturn(order);
    given(orderMapper.toSellerResponse(any(Order.class)))
        .willReturn(OrderFixture.aSellerOrderResponse(42L));

    // when
    SellerOrderResponse result = orderService.noShowOrder(2L, 42L);

    // then
    assertThat(result).isNotNull();
    assertThat(captor.getValue().getStatus()).isEqualTo(OrderStatus.NO_SHOW);
  }

  @Test
  void 준비중_상태에서_미수령시_409() {
    // given — PREPARING 주문 → READY 가 아니므로 미수령 불가
    Store store = OrderFixture.aStore();
    Order order =
        OrderFixture.anOrderWithStatus(OrderFixture.aCustomer(), store, OrderStatus.PREPARING);
    ReflectionTestUtils.setField(order, "id", 42L);
    given(orderRepository.findById(42L)).willReturn(Optional.of(order));

    assertThatThrownBy(() -> orderService.noShowOrder(2L, 42L))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.INVALID_ORDER_TRANSITION);
    then(orderRepository).should(never()).save(any());
  }

  @Test
  void 사장_미수령_주문없음_404() {
    given(orderRepository.findById(99L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> orderService.noShowOrder(2L, 99L))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.ORDER_NOT_FOUND);
  }

  @Test
  void 사장_미수령_타매장_403() {
    Store store = OrderFixture.aStore();
    Order order =
        OrderFixture.anOrderWithStatus(OrderFixture.aCustomer(), store, OrderStatus.READY);
    ReflectionTestUtils.setField(order, "id", 42L);
    given(orderRepository.findById(42L)).willReturn(Optional.of(order));

    assertThatThrownBy(() -> orderService.noShowOrder(999L, 42L))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.ORDER_FORBIDDEN);
  }

  // ── 쿠폰/포인트 혜택 ─────────────────────────────────────────────────────────

  @Test
  void 쿠폰_적용시_couponDiscount_저장() {
    // given — MENU 상품(3500×1), AMOUNT 쿠폰 1000, minOrder 2000
    Long userCouponId = 99L;
    givenStoreOpen();
    givenProduct();
    given(customerRepository.getReferenceById(CUSTOMER_ID)).willReturn(OrderFixture.aCustomer());

    UserCoupon uc = mock(UserCoupon.class);
    Coupon coupon =
        Coupon.builder()
            .kind(CouponKind.EVENT)
            .label("테스트쿠폰")
            .discountType(CouponDiscountType.AMOUNT)
            .discountValue(1000)
            .minOrder(2000)
            .validUntil(LocalDate.now().plusDays(30))
            .active(true)
            .build();
    given(uc.getCoupon()).willReturn(coupon);
    given(couponService.getUsableForOrder(userCouponId, CUSTOMER_ID)).willReturn(uc);

    ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
    given(orderRepository.save(orderCaptor.capture()))
        .willReturn(OrderFixture.anOrder(OrderFixture.aCustomer(), OrderFixture.aStore()));

    CreateOrderRequest req =
        new CreateOrderRequest(
            STORE_ID,
            List.of(new CreateOrderRequest.OrderItemRequest(ItemKind.MENU, PRODUCT_ID, 1)),
            new CreateOrderRequest.PickupRequest(PickupType.ASAP, null),
            null,
            "toss",
            true,
            null,
            userCouponId,
            null);

    // when
    PrepareOrderResponse response = orderService.createOrder(CUSTOMER_ID, req);

    // then — menuSubtotal=3500, couponDiscount=1000, finalAmount=2500
    Order saved = orderCaptor.getValue();
    assertThat(saved.getCouponDiscount()).isEqualByComparingTo(new BigDecimal("1000"));
    assertThat(saved.getFinalAmount()).isEqualByComparingTo(new BigDecimal("2500"));
    assertThat(response.amount()).isEqualByComparingTo(new BigDecimal("2500"));
  }

  @Test
  void 포인트_사용_한도_클램프() {
    // given — 잔액 1000, 요청 5000 → pointUsed = min(5000, min(1000, 3500)) = 1000
    givenStoreOpen();
    givenProduct();
    given(customerRepository.getReferenceById(CUSTOMER_ID)).willReturn(OrderFixture.aCustomer());
    given(pointService.getSummary(CUSTOMER_ID)).willReturn(new PointSummaryResponse(1000L));

    ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
    given(orderRepository.save(orderCaptor.capture()))
        .willReturn(OrderFixture.anOrder(OrderFixture.aCustomer(), OrderFixture.aStore()));

    CreateOrderRequest req =
        new CreateOrderRequest(
            STORE_ID,
            List.of(new CreateOrderRequest.OrderItemRequest(ItemKind.MENU, PRODUCT_ID, 1)),
            new CreateOrderRequest.PickupRequest(PickupType.ASAP, null),
            null,
            "toss",
            true,
            null,
            null,
            5000);

    // when
    orderService.createOrder(CUSTOMER_ID, req);

    // then — pointUsed=1000 (클램프됨), finalAmount=2500
    Order saved = orderCaptor.getValue();
    assertThat(saved.getPointUsed()).isEqualTo(1000L);
    assertThat(saved.getFinalAmount()).isEqualByComparingTo(new BigDecimal("2500")); // 3500 - 1000
  }

  @Test
  void 쿠폰_최소주문_미달_COUPON_NOT_AVAILABLE() {
    // given — coupon minOrder=5000, menuSubtotal=3500 → isApplicableTo=false
    Long userCouponId = 99L;
    givenStoreOpen();
    givenProduct();

    UserCoupon uc = mock(UserCoupon.class);
    Coupon coupon =
        Coupon.builder()
            .kind(CouponKind.EVENT)
            .label("최소주문 쿠폰")
            .discountType(CouponDiscountType.AMOUNT)
            .discountValue(1000)
            .minOrder(5000) // 5000 > menuSubtotal(3500)
            .validUntil(LocalDate.now().plusDays(30))
            .active(true)
            .build();
    given(uc.getCoupon()).willReturn(coupon);
    given(couponService.getUsableForOrder(userCouponId, CUSTOMER_ID)).willReturn(uc);

    CreateOrderRequest req =
        new CreateOrderRequest(
            STORE_ID,
            List.of(new CreateOrderRequest.OrderItemRequest(ItemKind.MENU, PRODUCT_ID, 1)),
            new CreateOrderRequest.PickupRequest(PickupType.ASAP, null),
            null,
            "toss",
            true,
            null,
            userCouponId,
            null);

    // when / then
    assertThatThrownBy(() -> orderService.createOrder(CUSTOMER_ID, req))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", CouponErrorCode.COUPON_NOT_AVAILABLE);
  }

  @Test
  void 포인트_요청0이면_getSummary_미호출() {
    // given — pointToUse=0 → getSummary 호출 안 됨
    givenStoreOpen();
    givenClearanceItem();
    givenDecrementStockSucceeds();
    givenOrderSaved();
    given(customerRepository.getReferenceById(CUSTOMER_ID)).willReturn(OrderFixture.aCustomer());

    CreateOrderRequest req =
        new CreateOrderRequest(
            STORE_ID,
            List.of(new CreateOrderRequest.OrderItemRequest(ItemKind.DEAL, CI_ID, 2)),
            new CreateOrderRequest.PickupRequest(PickupType.ASAP, null),
            null,
            "toss",
            true,
            null,
            null,
            0);

    // when
    orderService.createOrder(CUSTOMER_ID, req);

    // then
    then(pointService).should(never()).getSummary(anyLong());
  }

  @Test
  void 취소시_쿠폰포인트_복원() {
    // given — 쿠폰(99) + 포인트(500) 적용 PENDING 주문
    Store store = OrderFixture.aStore();
    Order order = OrderFixture.anOrderWithBenefits(OrderFixture.aCustomer(), store);
    ReflectionTestUtils.setField(order, "id", 42L);
    given(orderRepository.findById(42L)).willReturn(Optional.of(order));
    given(orderRepository.save(any(Order.class))).willReturn(order);
    given(orderMapper.toResponse(any(Order.class))).willReturn(OrderFixture.anOrderResponse(42L));
    given(paymentRepository.findByOrderId(42L)).willReturn(Optional.empty());

    // when
    orderService.cancelOrder(CUSTOMER_ID, 42L);

    // then
    then(couponService).should().restore(99L);
    then(pointService).should().restore(any(Order.class), eq(500L));
  }

  @Test
  void 거절시_복원() {
    // given — 쿠폰(99) + 포인트(500) 적용 PENDING 주문
    Store store = OrderFixture.aStore();
    Order order = OrderFixture.anOrderWithBenefits(OrderFixture.aCustomer(), store);
    ReflectionTestUtils.setField(order, "id", 42L);
    given(orderRepository.findById(42L)).willReturn(Optional.of(order));
    given(orderRepository.save(any(Order.class))).willReturn(order);
    given(orderMapper.toSellerResponse(any(Order.class)))
        .willReturn(OrderFixture.aSellerOrderResponse(42L));
    given(paymentRepository.findByOrderId(42L)).willReturn(Optional.empty());

    // when
    orderService.rejectOrder(2L, 42L); // seller.id=2 matches aStore().seller.id

    // then
    then(couponService).should().restore(99L);
    then(pointService).should().restore(any(Order.class), eq(500L));
  }

  @Test
  void 완료시_적립() {
    // given — READY 주문 with earnedPoints=45
    Store store = OrderFixture.aStore();
    Order order = OrderFixture.anOrderWithBenefits(OrderFixture.aCustomer(), store);
    ReflectionTestUtils.setField(order, "status", OrderStatus.READY);
    ReflectionTestUtils.setField(order, "id", 42L);
    given(orderRepository.findById(42L)).willReturn(Optional.of(order));
    given(orderRepository.save(any(Order.class))).willReturn(order);
    given(orderMapper.toSellerResponse(any(Order.class)))
        .willReturn(OrderFixture.aSellerOrderResponse(42L));

    // when
    orderService.completeOrder(2L, 42L);

    // then
    then(pointService).should().earn(any(Order.class), eq(45L));
  }
}
