package com.magampick.order.service;

import com.magampick.clearance.domain.ClearanceItem;
import com.magampick.clearance.domain.ClearanceItemStatus;
import com.magampick.clearance.exception.ClearanceItemErrorCode;
import com.magampick.clearance.repository.ClearanceItemRepository;
import com.magampick.customer.domain.Customer;
import com.magampick.customer.repository.CustomerRepository;
import com.magampick.global.exception.BusinessException;
import com.magampick.order.domain.ItemKind;
import com.magampick.order.domain.Order;
import com.magampick.order.domain.OrderItem;
import com.magampick.order.domain.OrderStatus;
import com.magampick.order.domain.PickupType;
import com.magampick.order.dto.CreateOrderRequest;
import com.magampick.order.dto.OrderResponse;
import com.magampick.order.dto.SellerOrderResponse;
import com.magampick.order.exception.OrderErrorCode;
import com.magampick.order.mapper.OrderMapper;
import com.magampick.order.repository.OrderRepository;
import com.magampick.payment.domain.Payment;
import com.magampick.payment.domain.PaymentStatus;
import com.magampick.payment.repository.PaymentRepository;
import com.magampick.payment.service.PaymentApproval;
import com.magampick.payment.service.PaymentCommand;
import com.magampick.payment.service.PaymentGateway;
import com.magampick.product.domain.Product;
import com.magampick.product.domain.ProductStatus;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 주문 생성 서비스. Phase 5A: 주문 생성 + stub 결제 자동승인 한 트랜잭션. 검증→재고차감→주문확정→픽업코드→결제→Payment 저장 순서. */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

  private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

  private final StoreRepository storeRepository;
  private final StoreBusinessHourRepository storeBusinessHourRepository;
  private final ClearanceItemRepository clearanceItemRepository;
  private final ProductRepository productRepository;
  private final CustomerRepository customerRepository;
  private final OrderRepository orderRepository;
  private final PaymentRepository paymentRepository;
  private final PaymentGateway paymentGateway;
  private final OrderMapper orderMapper;
  private final Clock clock;

  /**
   * 주문 생성 + stub 결제 자동승인 (한 @Transactional). 검증 순서: 결제동의 → 매장 영업 → 항목 검증/금액 재계산 → 교차검증 → 픽업 검증 →
   * 재고차감 → 주문생성 → 픽업코드 → 결제 stub 승인 → Payment 저장.
   */
  @Transactional
  public OrderResponse createOrder(Long customerId, CreateOrderRequest request) {

    // ── 1. 결제 동의 검증 ─────────────────────────────────────────────────────
    if (!Boolean.TRUE.equals(request.paymentAgreed())) {
      throw new BusinessException(OrderErrorCode.PAYMENT_NOT_AGREED);
    }

    // ── 2. 매장 조회 + 영업 상태 검증 ─────────────────────────────────────────
    Store store =
        storeRepository
            .findById(request.storeId())
            .orElseThrow(() -> new BusinessException(StoreErrorCode.STORE_NOT_FOUND));

    if (store.getOperationStatus() != OperationStatus.OPEN) {
      throw new BusinessException(StoreErrorCode.STORE_CLOSED);
    }

    LocalDate today = LocalDate.now(clock);
    DayOfWeek todayDow = today.getDayOfWeek();
    StoreBusinessHour todayHours =
        storeBusinessHourRepository
            .findByStoreIdAndDayOfWeek(store.getId(), todayDow)
            .orElseThrow(() -> new BusinessException(StoreErrorCode.STORE_CLOSED));

    // ── 3. 항목 검증 + 서버 금액 재계산 ─────────────────────────────────────
    List<CreateOrderRequest.OrderItemRequest> itemReqs = request.items();
    if (itemReqs == null || itemReqs.isEmpty()) {
      throw new BusinessException(OrderErrorCode.EMPTY_ORDER);
    }

    List<ResolvedItem> resolvedItems = new ArrayList<>();
    BigDecimal normalTotal = BigDecimal.ZERO;
    BigDecimal discountTotal = BigDecimal.ZERO;
    LocalDateTime now = LocalDateTime.now(clock);

    for (CreateOrderRequest.OrderItemRequest itemReq : itemReqs) {
      if (itemReq.kind() == ItemKind.DEAL) {
        ClearanceItem ci =
            clearanceItemRepository
                .findByIdWithStoreAndProduct(itemReq.refId())
                .orElseThrow(
                    () -> new BusinessException(ClearanceItemErrorCode.CLEARANCE_ITEM_NOT_FOUND));

        // 같은 매장 확인
        if (!ci.getStore().getId().equals(request.storeId())) {
          throw new BusinessException(OrderErrorCode.MIXED_STORE);
        }
        // 판매 상태 확인 (OPEN + 마감 전)
        if (ci.getStatus() != ClearanceItemStatus.OPEN || now.isAfter(ci.getPickupEndAt())) {
          throw new BusinessException(ClearanceItemErrorCode.CLEARANCE_CLOSED);
        }

        BigDecimal regular = ci.getRegularPrice();
        BigDecimal sale = ci.getSalePrice();
        int qty = itemReq.quantity();
        normalTotal = normalTotal.add(regular.multiply(BigDecimal.valueOf(qty)));
        discountTotal = discountTotal.add(regular.subtract(sale).multiply(BigDecimal.valueOf(qty)));

        String imageUrl = ci.getProduct() != null ? ci.getProduct().getImageUrl() : null;
        resolvedItems.add(
            new ResolvedItem(ItemKind.DEAL, ci, null, ci.getName(), regular, sale, imageUrl, qty));

      } else { // MENU
        Product product =
            productRepository
                .findByIdAndDeletedAtIsNull(itemReq.refId())
                .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));

        // 같은 매장 확인
        if (!product.getStore().getId().equals(request.storeId())) {
          throw new BusinessException(OrderErrorCode.MIXED_STORE);
        }
        // 판매 상태 확인
        if (product.getStatus() != ProductStatus.ON_SALE) {
          throw new BusinessException(ProductErrorCode.PRODUCT_NOT_ON_SALE);
        }

        BigDecimal regular = product.getRegularPrice();
        int qty = itemReq.quantity();
        normalTotal = normalTotal.add(regular.multiply(BigDecimal.valueOf(qty)));

        resolvedItems.add(
            new ResolvedItem(
                ItemKind.MENU,
                null,
                product,
                product.getName(),
                regular,
                regular,
                product.getImageUrl(),
                qty));
      }
    }

    BigDecimal payTotal = normalTotal.subtract(discountTotal);

    // ── 4. 금액 교차검증 ───────────────────────────────────────────────────────
    if (request.amounts() != null) {
      CreateOrderRequest.AmountsRequest reqAmounts = request.amounts();
      if (reqAmounts.normalTotal().compareTo(normalTotal) != 0
          || reqAmounts.discountTotal().compareTo(discountTotal) != 0
          || reqAmounts.payTotal().compareTo(payTotal) != 0) {
        throw new BusinessException(OrderErrorCode.AMOUNT_MISMATCH);
      }
    }

    // ── 5. 픽업 시간 검증 ──────────────────────────────────────────────────────
    LocalDateTime pickupTime = resolvePickupTime(request.pickup(), todayHours, today, now);

    // ── 6. 재고 차감 (DEAL 항목) — 조건부 UPDATE, 0 행이면 재고 부족 ────────
    for (ResolvedItem item : resolvedItems) {
      if (item.kind() == ItemKind.DEAL) {
        int updated =
            clearanceItemRepository.decrementStock(item.clearanceItem().getId(), item.qty());
        if (updated == 0) {
          throw new BusinessException(ClearanceItemErrorCode.OUT_OF_STOCK);
        }
      }
    }

    // ── 7. 픽업 코드 생성 (4자리) ─────────────────────────────────────────────
    String pickupCode = String.format("%04d", ThreadLocalRandom.current().nextInt(10000));

    // ── 8. 주문 생성 ──────────────────────────────────────────────────────────
    Customer customer = customerRepository.getReferenceById(customerId);
    Order order =
        Order.builder()
            .customer(customer)
            .store(store)
            .status(OrderStatus.PENDING)
            .totalPrice(payTotal)
            .pickupTime(pickupTime)
            .pickupType(request.pickup().type())
            .pickupCode(pickupCode)
            .memo(request.memo())
            .normalTotal(normalTotal)
            .discountTotal(discountTotal)
            .build();

    // 주문 항목 추가
    for (ResolvedItem item : resolvedItems) {
      OrderItem oi;
      if (item.kind() == ItemKind.DEAL) {
        oi =
            OrderItem.forDeal(
                order,
                item.clearanceItem(),
                item.name(),
                item.originalPrice(),
                item.imageUrl(),
                item.qty(),
                item.unitPrice());
      } else {
        oi =
            OrderItem.forMenu(
                order,
                item.product(),
                item.name(),
                item.originalPrice(),
                item.imageUrl(),
                item.qty(),
                item.unitPrice());
      }
      order.addOrderItem(oi);
    }

    Order savedOrder = orderRepository.save(order);

    // ── 9. stub 결제 승인 ─────────────────────────────────────────────────────
    PaymentCommand paymentCommand =
        new PaymentCommand("order-" + savedOrder.getId(), payTotal, request.paymentMethod());
    PaymentApproval approval = paymentGateway.approve(paymentCommand);

    if (approval.status() != PaymentStatus.APPROVED) {
      throw new BusinessException(OrderErrorCode.PAYMENT_FAILED);
    }

    // ── 10. Payment 저장 ──────────────────────────────────────────────────────
    Payment payment =
        Payment.builder()
            .order(savedOrder)
            .provider("TOSS")
            .method(request.paymentMethod())
            .paymentKey(approval.paymentKey())
            .amount(payTotal)
            .status(approval.status())
            .approvedAt(approval.approvedAt())
            .build();
    paymentRepository.save(payment);

    log.info(
        "주문 생성됨. orderId={}, customerId={}, storeId={}, payTotal={}",
        savedOrder.getId(),
        customerId,
        store.getId(),
        payTotal);

    return orderMapper.toResponse(savedOrder);
  }

  /**
   * 픽업 시각 계산 + 검증. ASAP = null, SLOT = 오늘 날짜(clock 기준) + 시각.
   *
   * <p>SLOT 검증 순서: 파싱 → 15분 단위 → 현재 시각 이후(과거/현재 거부) → 영업 개시 이후 → 영업종료 이전.
   */
  private LocalDateTime resolvePickupTime(
      CreateOrderRequest.PickupRequest pickupReq,
      StoreBusinessHour todayHours,
      LocalDate today,
      LocalDateTime now) {
    if (pickupReq.type() == PickupType.ASAP) {
      return null;
    }
    // SLOT: time 필드 필수
    String timeStr = pickupReq.time();
    if (timeStr == null || timeStr.isBlank()) {
      throw new BusinessException(OrderErrorCode.INVALID_PICKUP_TIME);
    }
    LocalTime slotTime;
    try {
      slotTime = LocalTime.parse(timeStr, TIME_FORMATTER);
    } catch (DateTimeParseException e) {
      throw new BusinessException(OrderErrorCode.INVALID_PICKUP_TIME);
    }
    // 15분 단위 확인
    if (slotTime.getMinute() % 15 != 0) {
      throw new BusinessException(OrderErrorCode.INVALID_PICKUP_TIME);
    }
    // 현재 시각 이후 확인 — 과거/현재 픽업 거부 (strictly after)
    if (!slotTime.isAfter(now.toLocalTime())) {
      throw new BusinessException(OrderErrorCode.INVALID_PICKUP_TIME);
    }
    // 영업 개시 이후 확인
    if (slotTime.isBefore(todayHours.getOpenTime())) {
      throw new BusinessException(OrderErrorCode.INVALID_PICKUP_TIME);
    }
    // 영업종료 이전 확인
    if (!slotTime.isBefore(todayHours.getCloseTime())) {
      throw new BusinessException(OrderErrorCode.INVALID_PICKUP_TIME);
    }
    return today.atTime(slotTime);
  }

  // ── 소비자 segment → statuses ────────────────────────────────────────────────

  private static final List<OrderStatus> CUSTOMER_ALL =
      List.of(
          OrderStatus.PENDING,
          OrderStatus.PREPARING,
          OrderStatus.READY,
          OrderStatus.COMPLETED,
          OrderStatus.NO_SHOW,
          OrderStatus.REJECTED,
          OrderStatus.CANCELLED);

  private static final List<OrderStatus> CUSTOMER_PICKUP_WAITING =
      List.of(OrderStatus.PENDING, OrderStatus.PREPARING, OrderStatus.READY);

  private static final List<OrderStatus> CUSTOMER_DONE =
      List.of(
          OrderStatus.COMPLETED, OrderStatus.CANCELLED, OrderStatus.REJECTED, OrderStatus.NO_SHOW);

  // ── 사장 segment → statuses ──────────────────────────────────────────────────

  private static final List<OrderStatus> SELLER_ALL =
      List.of(
          OrderStatus.PENDING,
          OrderStatus.PREPARING,
          OrderStatus.READY,
          OrderStatus.COMPLETED,
          OrderStatus.NO_SHOW,
          OrderStatus.REJECTED,
          OrderStatus.CANCELLED);

  private static final List<OrderStatus> SELLER_CANCELLED =
      List.of(OrderStatus.CANCELLED, OrderStatus.REJECTED, OrderStatus.NO_SHOW);

  /** 소비자 주문 목록. segment → statuses 변환 후 최신순 반환. */
  public List<OrderResponse> listMyOrders(Long customerId, String segment) {
    List<OrderStatus> statuses = resolveCustomerSegment(segment);
    return orderRepository
        .findByCustomerIdAndStatusInOrderByCreatedAtDesc(customerId, statuses)
        .stream()
        .map(orderMapper::toResponse)
        .toList();
  }

  /** 소비자 주문 상세. 본인 주문이 아니면 ORDER_FORBIDDEN 403. */
  public OrderResponse getMyOrder(Long customerId, Long orderId) {
    Order order =
        orderRepository
            .findById(orderId)
            .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
    if (!order.getCustomer().getId().equals(customerId)) {
      throw new BusinessException(OrderErrorCode.ORDER_FORBIDDEN);
    }
    return orderMapper.toResponse(order);
  }

  /** 사장 매장 주문 목록. 매장 소유권 검증 후 segment 필터 적용. */
  public List<SellerOrderResponse> listStoreOrders(Long sellerId, Long storeId, String segment) {
    storeRepository
        .findByIdAndSellerId(storeId, sellerId)
        .orElseThrow(() -> new BusinessException(StoreErrorCode.STORE_ACCESS_DENIED));
    List<OrderStatus> statuses = resolveSellerSegment(segment);
    return orderRepository.findByStoreIdAndStatusInOrderByCreatedAtDesc(storeId, statuses).stream()
        .map(orderMapper::toSellerResponse)
        .toList();
  }

  /** 사장 주문 상세. 본인 매장 주문이 아니면 ORDER_FORBIDDEN 403. */
  public SellerOrderResponse getStoreOrder(Long sellerId, Long orderId) {
    Order order =
        orderRepository
            .findById(orderId)
            .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
    if (!order.getStore().getSeller().getId().equals(sellerId)) {
      throw new BusinessException(OrderErrorCode.ORDER_FORBIDDEN);
    }
    return orderMapper.toSellerResponse(order);
  }

  private List<OrderStatus> resolveCustomerSegment(String segment) {
    if (segment == null) return CUSTOMER_ALL;
    return switch (segment.toUpperCase()) {
      case "PICKUP_WAITING" -> CUSTOMER_PICKUP_WAITING;
      case "DONE" -> CUSTOMER_DONE;
      default -> CUSTOMER_ALL;
    };
  }

  private List<OrderStatus> resolveSellerSegment(String segment) {
    if (segment == null) return SELLER_ALL;
    return switch (segment.toUpperCase()) {
      case "PENDING" -> List.of(OrderStatus.PENDING);
      case "PREPARING" -> List.of(OrderStatus.PREPARING);
      case "READY" -> List.of(OrderStatus.READY);
      case "COMPLETED" -> List.of(OrderStatus.COMPLETED);
      case "CANCELLED" -> SELLER_CANCELLED;
      default -> SELLER_ALL;
    };
  }

  /** 서비스 내부 항목 컨텍스트. */
  private record ResolvedItem(
      ItemKind kind,
      ClearanceItem clearanceItem,
      Product product,
      String name,
      BigDecimal originalPrice,
      BigDecimal unitPrice,
      String imageUrl,
      int qty) {}
}
