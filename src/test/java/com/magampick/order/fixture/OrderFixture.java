package com.magampick.order.fixture;

import com.magampick.clearance.domain.ClearanceItem;
import com.magampick.clearance.domain.ClearanceItemStatus;
import com.magampick.customer.domain.Customer;
import com.magampick.global.common.GeometryUtil;
import com.magampick.order.domain.ItemKind;
import com.magampick.order.domain.Order;
import com.magampick.order.domain.OrderItem;
import com.magampick.order.domain.OrderStatus;
import com.magampick.order.domain.PickupType;
import com.magampick.order.dto.CreateOrderRequest;
import com.magampick.order.dto.CreateOrderRequest.AmountsRequest;
import com.magampick.order.dto.CreateOrderRequest.OrderItemRequest;
import com.magampick.order.dto.CreateOrderRequest.PickupRequest;
import com.magampick.order.dto.OrderResponse;
import com.magampick.order.dto.SellerOrderResponse;
import com.magampick.product.domain.Product;
import com.magampick.product.domain.ProductCategory;
import com.magampick.product.domain.ProductStatus;
import com.magampick.seller.domain.Seller;
import com.magampick.store.domain.OperationStatus;
import com.magampick.store.domain.Store;
import com.magampick.store.domain.StoreBusinessHour;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.test.util.ReflectionTestUtils;

/** 주문 도메인 테스트 픽스처. */
public class OrderFixture {

  private OrderFixture() {}

  // ── 도메인 객체 ─────────────────────────────────────────────────────────────

  public static Customer aCustomer() {
    Customer c =
        Customer.builder().email("customer@test.com").passwordHash("hash").nickname("테스터").build();
    ReflectionTestUtils.setField(c, "id", 1L);
    return c;
  }

  public static Seller aSeller() {
    Seller s =
        Seller.builder().email("seller@test.com").passwordHash("hash").ownerName("사장님").build();
    ReflectionTestUtils.setField(s, "id", 2L);
    return s;
  }

  public static Store aStore() {
    return aStore(OperationStatus.OPEN);
  }

  public static Store aStore(OperationStatus operationStatus) {
    Store s =
        Store.builder()
            .seller(aSeller())
            .businessNumber("1234567890")
            .name("동네빵집")
            .roadAddress("서울 강남구 테헤란로 1")
            .zonecode("06158")
            .location(GeometryUtil.toPoint(37.5, 127.0))
            .phone("0212345678")
            .operationStatus(operationStatus)
            .build();
    ReflectionTestUtils.setField(s, "id", 10L);
    return s;
  }

  public static StoreBusinessHour aTodayBusinessHour(Store store) {
    DayOfWeek today = LocalDate.now().getDayOfWeek();
    return StoreBusinessHour.builder()
        .store(store)
        .dayOfWeek(today)
        .openTime(LocalTime.of(9, 0))
        .closeTime(LocalTime.of(21, 0))
        .build();
  }

  public static ClearanceItem aClearanceItem(Store store) {
    ClearanceItem ci =
        ClearanceItem.builder()
            .store(store)
            .name("크로아상")
            .regularPrice(new BigDecimal("4500"))
            .salePrice(new BigDecimal("3000"))
            .totalQuantity(10)
            .pickupStartAt(LocalDateTime.now().minusHours(1))
            .pickupEndAt(LocalDateTime.now().plusHours(3))
            .build();
    ReflectionTestUtils.setField(ci, "id", 100L);
    ReflectionTestUtils.setField(ci, "status", ClearanceItemStatus.OPEN);
    return ci;
  }

  /**
   * 만료 시각이 먼 미래인 ClearanceItem. 고정 Clock 테스트에서 픽업마감 검증과 무관하게 재사용한다.
   *
   * @param store 소속 매장
   */
  public static ClearanceItem aClearanceItemNonExpiring(Store store) {
    ClearanceItem ci =
        ClearanceItem.builder()
            .store(store)
            .name("크로아상")
            .regularPrice(new BigDecimal("4500"))
            .salePrice(new BigDecimal("3000"))
            .totalQuantity(10)
            .pickupStartAt(LocalDateTime.of(2000, 1, 1, 0, 0))
            .pickupEndAt(LocalDateTime.of(2099, 12, 31, 23, 59))
            .build();
    ReflectionTestUtils.setField(ci, "id", 100L);
    ReflectionTestUtils.setField(ci, "status", ClearanceItemStatus.OPEN);
    return ci;
  }

  public static ClearanceItem aClosedClearanceItem(Store store) {
    ClearanceItem ci = aClearanceItem(store);
    ReflectionTestUtils.setField(ci, "status", ClearanceItemStatus.CLOSED);
    return ci;
  }

  public static ClearanceItem anExpiredClearanceItem(Store store) {
    ClearanceItem ci =
        ClearanceItem.builder()
            .store(store)
            .name("마감지난빵")
            .regularPrice(new BigDecimal("4500"))
            .salePrice(new BigDecimal("3000"))
            .totalQuantity(5)
            .pickupStartAt(LocalDateTime.now().minusHours(5))
            .pickupEndAt(LocalDateTime.now().minusHours(1))
            .build();
    ReflectionTestUtils.setField(ci, "id", 101L);
    ReflectionTestUtils.setField(ci, "status", ClearanceItemStatus.OPEN);
    return ci;
  }

  public static Product aProduct(Store store) {
    Product p =
        Product.builder()
            .store(store)
            .name("아메리카노")
            .regularPrice(new BigDecimal("3500"))
            .imageUrl("/uploads/americano.jpg")
            .status(ProductStatus.ON_SALE)
            .category(ProductCategory.BEVERAGE)
            .build();
    ReflectionTestUtils.setField(p, "id", 200L);
    return p;
  }

  public static Product aSoldOutProduct(Store store) {
    Product p = aProduct(store);
    ReflectionTestUtils.setField(p, "status", ProductStatus.SOLD_OUT);
    return p;
  }

  // ── DTO ─────────────────────────────────────────────────────────────────────

  public static CreateOrderRequest aDealOrderRequest(Long storeId, Long clearanceItemId) {
    return new CreateOrderRequest(
        storeId,
        List.of(new OrderItemRequest(ItemKind.DEAL, clearanceItemId, 2)),
        new PickupRequest(PickupType.ASAP, null),
        null,
        "toss",
        true,
        null);
  }

  public static CreateOrderRequest aMenuOrderRequest(Long storeId, Long productId) {
    return new CreateOrderRequest(
        storeId,
        List.of(new OrderItemRequest(ItemKind.MENU, productId, 1)),
        new PickupRequest(PickupType.ASAP, null),
        null,
        "toss",
        true,
        null);
  }

  public static CreateOrderRequest aMixedOrderRequest(
      Long storeId, Long clearanceItemId, Long productId) {
    return new CreateOrderRequest(
        storeId,
        List.of(
            new OrderItemRequest(ItemKind.DEAL, clearanceItemId, 1),
            new OrderItemRequest(ItemKind.MENU, productId, 1)),
        new PickupRequest(PickupType.ASAP, null),
        null,
        "toss",
        true,
        null);
  }

  public static CreateOrderRequest withAmounts(
      CreateOrderRequest base,
      BigDecimal normalTotal,
      BigDecimal discountTotal,
      BigDecimal payTotal) {
    return new CreateOrderRequest(
        base.storeId(),
        base.items(),
        base.pickup(),
        base.memo(),
        base.paymentMethod(),
        base.paymentAgreed(),
        new AmountsRequest(normalTotal, discountTotal, payTotal));
  }

  public static CreateOrderRequest withPickup(CreateOrderRequest base, PickupRequest pickup) {
    return new CreateOrderRequest(
        base.storeId(),
        base.items(),
        pickup,
        base.memo(),
        base.paymentMethod(),
        base.paymentAgreed(),
        base.amounts());
  }

  public static CreateOrderRequest withPaymentAgreed(CreateOrderRequest base, Boolean agreed) {
    return new CreateOrderRequest(
        base.storeId(),
        base.items(),
        base.pickup(),
        base.memo(),
        base.paymentMethod(),
        agreed,
        base.amounts());
  }

  // ── Order 엔티티 ─────────────────────────────────────────────────────────────

  /**
   * Order 픽스처. 저장 전(transient) 상태이므로 id 는 DB 에서 할당됨. review 등 다른 도메인 테스트에서 실제 저장 시 사용. 서비스 단위
   * 테스트에서는 orderRepository.save() 가 Mockito 로 대체되므로 id 불필요.
   */
  public static Order anOrder(Customer customer, Store store) {
    return Order.builder()
        .customer(customer)
        .store(store)
        .status(OrderStatus.PENDING)
        .totalPrice(new BigDecimal("6000"))
        .pickupType(PickupType.ASAP)
        .pickupCode("3827")
        .normalTotal(new BigDecimal("9000"))
        .discountTotal(new BigDecimal("3000"))
        .build();
  }

  // ── OrderItem 엔티티 ──────────────────────────────────────────────────────────

  /**
   * DEAL OrderItem 픽스처. review 도메인 테스트에서 주문 항목 연결 시 사용. cascade 저장 전에 order.addOrderItem() 또는
   * order.getOrderItems().add(item) 필요.
   */
  public static OrderItem anOrderItem(Order order, ClearanceItem clearanceItem) {
    return OrderItem.forDeal(
        order,
        clearanceItem,
        clearanceItem.getName(),
        clearanceItem.getRegularPrice(),
        null,
        1,
        clearanceItem.getSalePrice());
  }

  // ── Response ─────────────────────────────────────────────────────────────────

  public static OrderResponse anOrderResponse(Long orderId) {
    return new OrderResponse(
        orderId,
        String.format("%04d", orderId),
        10L,
        "동네빵집",
        "0212345678",
        List.of(
            new OrderResponse.OrderItemResponse(
                1L, "DEAL", "크로아상", null, new BigDecimal("4500"), new BigDecimal("3000"), 2)),
        new OrderResponse.PickupResponse("ASAP", null),
        null,
        new OrderResponse.OrderAmountsResponse(
            new BigDecimal("9000"), new BigDecimal("3000"), new BigDecimal("6000")),
        "3827",
        "PENDING",
        "toss",
        OffsetDateTime.of(2026, 6, 8, 10, 30, 0, 0, ZoneOffset.ofHours(9)),
        null,
        null);
  }

  public static SellerOrderResponse aSellerOrderResponse(Long orderId) {
    return new SellerOrderResponse(
        orderId,
        String.format("%04d", orderId),
        10L,
        "동네빵집",
        "0212345678",
        List.of(
            new OrderResponse.OrderItemResponse(
                1L, "DEAL", "크로아상", null, new BigDecimal("4500"), new BigDecimal("3000"), 2)),
        new OrderResponse.PickupResponse("ASAP", null),
        null,
        new OrderResponse.OrderAmountsResponse(
            new BigDecimal("9000"), new BigDecimal("3000"), new BigDecimal("6000")),
        "3827",
        "PENDING",
        "toss",
        OffsetDateTime.of(2026, 6, 8, 10, 30, 0, 0, ZoneOffset.ofHours(9)),
        "테스터",
        "01012345678",
        null,
        null,
        null,
        null,
        null);
  }

  /** storePhone=null 인 응답 — @JsonInclude(NON_NULL) 직렬화 검증용. */
  public static OrderResponse anOrderResponseNullPhone(Long orderId) {
    return new OrderResponse(
        orderId,
        String.format("%04d", orderId),
        10L,
        "동네빵집",
        null,
        List.of(
            new OrderResponse.OrderItemResponse(
                1L, "DEAL", "크로아상", null, new BigDecimal("4500"), new BigDecimal("3000"), 2)),
        new OrderResponse.PickupResponse("ASAP", null),
        null,
        new OrderResponse.OrderAmountsResponse(
            new BigDecimal("9000"), new BigDecimal("3000"), new BigDecimal("6000")),
        "3827",
        "PENDING",
        "toss",
        OffsetDateTime.of(2026, 6, 8, 10, 30, 0, 0, ZoneOffset.ofHours(9)),
        null,
        null);
  }
}
