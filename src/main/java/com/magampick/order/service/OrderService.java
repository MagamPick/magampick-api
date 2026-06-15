package com.magampick.order.service;

import com.magampick.clearance.domain.ClearanceItem;
import com.magampick.clearance.exception.ClearanceItemErrorCode;
import com.magampick.clearance.repository.ClearanceItemRepository;
import com.magampick.coupon.domain.UserCoupon;
import com.magampick.coupon.exception.CouponErrorCode;
import com.magampick.coupon.service.CouponService;
import com.magampick.customer.domain.Customer;
import com.magampick.customer.repository.CustomerRepository;
import com.magampick.global.exception.BusinessException;
import com.magampick.notification.domain.NotificationCategory;
import com.magampick.notification.service.NotificationService;
import com.magampick.order.domain.ItemKind;
import com.magampick.order.domain.Order;
import com.magampick.order.domain.OrderItem;
import com.magampick.order.domain.OrderStatus;
import com.magampick.order.domain.PickupType;
import com.magampick.order.dto.CreateOrderRequest;
import com.magampick.order.dto.OrderResponse;
import com.magampick.order.dto.PrepareOrderResponse;
import com.magampick.order.dto.SellerOrderResponse;
import com.magampick.order.exception.OrderErrorCode;
import com.magampick.order.mapper.OrderMapper;
import com.magampick.order.repository.OrderRepository;
import com.magampick.payment.repository.PaymentRepository;
import com.magampick.payment.service.PaymentCancellationCommand;
import com.magampick.payment.service.PaymentGateway;
import com.magampick.point.service.PointService;
import com.magampick.product.domain.Product;
import com.magampick.product.exception.ProductErrorCode;
import com.magampick.product.repository.ProductRepository;
import com.magampick.refund.mapper.RefundMapper;
import com.magampick.refund.repository.RefundRepository;
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
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 주문 생성·관리 서비스. createOrder 는 AWAITING_PAYMENT 임시 생성, 결제 확인은 TossConfirmService. */
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
  private final RefundRepository refundRepository;
  private final RefundMapper refundMapper;
  private final CouponService couponService;
  private final PointService pointService;
  private final NotificationService notificationService;
  private final Clock clock;

  /**
   * 주문 임시 생성 (AWAITING_PAYMENT). 검증 → 재고차감 → 주문 생성 → PrepareOrderResponse 반환. 결제 확인은
   * TossConfirmService.confirmPayment().
   */
  @Transactional
  public PrepareOrderResponse createOrder(Long customerId, CreateOrderRequest request) {

    // ── 1. 결제 동의 검증 ─────────────────────────────────────────────────────
    if (!Boolean.TRUE.equals(request.paymentAgreed())) {
      throw new BusinessException(OrderErrorCode.PAYMENT_NOT_AGREED);
    }

    // ── 2. 매장 조회 + 영업 상태 검증 ─────────────────────────────────────────
    Store store =
        storeRepository
            .findById(request.storeId())
            .orElseThrow(() -> new BusinessException(StoreErrorCode.STORE_NOT_FOUND));

    if (!store.isOpen()) {
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
    BigDecimal menuSubtotal = BigDecimal.ZERO;
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
        if (!ci.isOpen() || now.isAfter(ci.getPickupEndAt())) {
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
        if (!product.isOnSale()) {
          throw new BusinessException(ProductErrorCode.PRODUCT_NOT_ON_SALE);
        }

        BigDecimal regular = product.getRegularPrice();
        int qty = itemReq.quantity();
        menuSubtotal = menuSubtotal.add(regular.multiply(BigDecimal.valueOf(qty)));
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

    // ── 4A. 쿠폰 / 포인트 혜택 계산 ────────────────────────────────────────────
    BigDecimal couponDiscount = BigDecimal.ZERO;
    Long userCouponId = request.userCouponId();
    if (userCouponId != null) {
      UserCoupon uc = couponService.getUsableForOrder(userCouponId, customerId);
      if (!uc.isApplicableTo(menuSubtotal)) {
        throw new BusinessException(CouponErrorCode.COUPON_NOT_AVAILABLE);
      }
      couponDiscount = uc.calcDiscount(menuSubtotal);
    }
    BigDecimal afterCoupon = payTotal.subtract(couponDiscount);
    // 포인트 (요청 있을 때만 잔액 조회)
    long pointUsed = 0L;
    long requested = request.pointToUse() != null ? request.pointToUse() : 0L;
    if (requested > 0) {
      long balance = pointService.getSummary(customerId).balance();
      long cap = Math.min(balance, afterCoupon.longValueExact()); // afterCoupon >= 0
      pointUsed = Math.max(0L, Math.min(requested, cap));
    }
    BigDecimal finalAmount = afterCoupon.subtract(BigDecimal.valueOf(pointUsed));
    long earnedPoints = finalAmount.longValueExact() / 100; // floor (>=0)

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

    // ── 5.5 동일 재시도 판정 — 모든 조건이 같은 기존 AWAITING 주문이 있으면 그대로 재사용 ──────────
    // 카드 거절·창 닫음 등으로 같은 장바구니를 다시 결제 시도하면, 새 주문·재고 차감·기존주문 취소를 모두 건너뛰고
    // 기존 주문을 그대로 돌려준다 (재고는 그 주문이 이미 점유 중). 결제만 재시도하면 되므로 재고 churn / CANCELLED 찌꺼기 방지.
    Order reusableOrder =
        findReusableAwaitingOrder(
            customerId, request, resolvedItems, userCouponId, pointUsed, pickupTime, finalAmount);
    if (reusableOrder != null) {
      log.info(
          "동일 재시도 — 기존 AWAITING 주문 재사용 (재고/주문 재생성 안 함). orderId={}, customerId={}",
          reusableOrder.getId(),
          customerId);
      return new PrepareOrderResponse(
          reusableOrder.getId(),
          "order-" + reusableOrder.getId(),
          reusableOrder.getFinalAmount(),
          buildOrderName(resolvedItems));
    }

    // 동일 주문 없음(신규 / 장바구니 변경 / 가격 변동) → 기존 AWAITING 주문 취소 + 재고복원 후 신규 생성 (재고 다중누수 차단)
    cancelExistingAwaitingOrders(customerId);

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

    // ── 8. 주문 임시 생성 (AWAITING_PAYMENT) ─────────────────────────────────
    Customer customer = customerRepository.getReferenceById(customerId);
    Order order =
        Order.builder()
            .customer(customer)
            .store(store)
            .status(OrderStatus.AWAITING_PAYMENT)
            .totalPrice(payTotal)
            .pickupTime(pickupTime)
            .pickupType(request.pickup().type())
            .pickupCode(pickupCode)
            .memo(request.memo())
            .normalTotal(normalTotal)
            .discountTotal(discountTotal)
            .couponDiscount(couponDiscount.signum() > 0 ? couponDiscount : null)
            .pointUsed(pointUsed > 0 ? pointUsed : null)
            .earnedPoints(earnedPoints > 0 ? earnedPoints : null)
            .finalAmount(finalAmount)
            .userCouponId(userCouponId)
            .build();

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

    String tossOrderId = "order-" + savedOrder.getId();
    String orderName = buildOrderName(resolvedItems);

    log.info(
        "주문 임시 생성됨. orderId={}, customerId={}, storeId={}, payTotal={}, finalAmount={}",
        savedOrder.getId(),
        customerId,
        store.getId(),
        payTotal,
        finalAmount);

    return new PrepareOrderResponse(savedOrder.getId(), tossOrderId, finalAmount, orderName);
  }

  private String buildOrderName(List<ResolvedItem> items) {
    if (items.isEmpty()) return "주문";
    String first = items.get(0).name();
    if (items.size() == 1) {
      return first + " " + items.get(0).qty() + "개";
    }
    return first + " 외 " + (items.size() - 1) + "건";
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

  /** 소비자 주문 상세. 본인 주문이 아니면 ORDER_FORBIDDEN 403. 환불 정보가 있으면 refund 필드 포함. */
  public OrderResponse getMyOrder(Long customerId, Long orderId) {
    Order order =
        orderRepository
            .findById(orderId)
            .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
    if (!order.isOwnedBy(customerId)) {
      throw new BusinessException(OrderErrorCode.ORDER_FORBIDDEN);
    }
    OrderResponse base = orderMapper.toResponse(order);
    return refundRepository
        .findByOrderId(orderId)
        .map(refund -> orderMapper.withRefund(base, refundMapper.toInfoResponse(refund)))
        .orElse(base);
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
    if (!order.getStore().isOwnedBy(sellerId)) {
      throw new BusinessException(OrderErrorCode.ORDER_FORBIDDEN);
    }
    return orderMapper.toSellerResponse(order);
  }

  // ── 주문 상태 전이 ────────────────────────────────────────────────────────────

  /** 소비자 주문 취소. PENDING → CANCELLED. 토스 환불 후 상태 전이. */
  @Transactional
  public OrderResponse cancelOrder(Long customerId, Long orderId) {
    // 주문 조회
    Order order =
        orderRepository
            .findById(orderId)
            .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
    // 소유권 확인
    if (!order.isOwnedBy(customerId)) {
      throw new BusinessException(OrderErrorCode.ORDER_FORBIDDEN);
    }
    // 상태 전이 가능 여부 확인
    if (!order.isPending()) {
      throw new BusinessException(OrderErrorCode.INVALID_ORDER_TRANSITION);
    }
    // 결제 환불
    refundPayment(orderId, "고객 취소");
    // 상태 전이
    order.cancel(LocalDateTime.now(clock));
    // 혜택 복원
    restoreBenefits(order);
    Order saved = orderRepository.save(order);
    // 알림 발송
    notificationService.notifySeller(
        order.getStore().getSeller().getId(),
        "orderCancel",
        NotificationCategory.ORDER,
        "주문이 취소되었어요",
        order.getCustomer().getNickname() + "님이 주문을 취소했어요.",
        "/orders/" + saved.getId());
    return orderMapper.toResponse(saved);
  }

  /** 사장 주문 수락. PENDING → PREPARING. */
  @Transactional
  public SellerOrderResponse acceptOrder(Long sellerId, Long orderId) {
    Order order = findOrderForSeller(sellerId, orderId);
    // 상태 전이 가능 여부 확인
    if (!order.isPending()) {
      throw new BusinessException(OrderErrorCode.INVALID_ORDER_TRANSITION);
    }
    // 상태 전이
    order.accept(LocalDateTime.now(clock));
    Order saved = orderRepository.save(order);
    // 알림 발송
    notificationService.notifyCustomer(
        order.getCustomer().getId(),
        "orderRefund",
        NotificationCategory.ORDER,
        "주문이 수락되었어요",
        order.getStore().getName() + "에서 주문을 수락했어요.",
        "/orders/" + saved.getId());
    return orderMapper.toSellerResponse(saved);
  }

  /** 사장 주문 거절. PENDING → REJECTED. 토스 환불 후 상태 전이. */
  @Transactional
  public SellerOrderResponse rejectOrder(Long sellerId, Long orderId) {
    Order order = findOrderForSeller(sellerId, orderId);
    // 상태 전이 가능 여부 확인
    if (!order.isPending()) {
      throw new BusinessException(OrderErrorCode.INVALID_ORDER_TRANSITION);
    }
    // 결제 환불
    refundPayment(orderId, "사장 거절");
    // 상태 전이
    order.reject(LocalDateTime.now(clock));
    // 혜택 복원
    restoreBenefits(order);
    Order saved = orderRepository.save(order);
    // 알림 발송
    notificationService.notifyCustomer(
        order.getCustomer().getId(),
        "orderRefund",
        NotificationCategory.ORDER,
        "주문이 거절되었어요",
        order.getStore().getName() + "에서 주문을 거절했어요.",
        "/orders/" + saved.getId());
    return orderMapper.toSellerResponse(saved);
  }

  /** 사장 준비완료. PREPARING → READY. */
  @Transactional
  public SellerOrderResponse readyOrder(Long sellerId, Long orderId) {
    Order order = findOrderForSeller(sellerId, orderId);
    // 상태 전이 가능 여부 확인
    if (!order.isPreparing()) {
      throw new BusinessException(OrderErrorCode.INVALID_ORDER_TRANSITION);
    }
    // 상태 전이
    order.markReady(LocalDateTime.now(clock));
    Order saved = orderRepository.save(order);
    // 알림 발송
    notificationService.notifyCustomer(
        order.getCustomer().getId(),
        "orderRefund",
        NotificationCategory.ORDER,
        "픽업 준비가 완료되었어요",
        order.getStore().getName() + "에서 픽업 준비가 완료되었어요.",
        "/orders/" + saved.getId());
    return orderMapper.toSellerResponse(saved);
  }

  /** 사장 수령완료. READY → COMPLETED. */
  @Transactional
  public SellerOrderResponse completeOrder(Long sellerId, Long orderId) {
    Order order = findOrderForSeller(sellerId, orderId);
    // 상태 전이 가능 여부 확인
    if (!order.isReady()) {
      throw new BusinessException(OrderErrorCode.INVALID_ORDER_TRANSITION);
    }
    // 상태 전이
    order.complete(LocalDateTime.now(clock));
    // 포인트 적립
    if (order.hasEarnedPoints()) pointService.earn(order, order.getEarnedPoints());
    Order saved = orderRepository.save(order);
    // 알림 발송
    notificationService.notifyCustomer(
        order.getCustomer().getId(),
        "orderRefund",
        NotificationCategory.ORDER,
        "픽업이 완료되었어요",
        order.getStore().getName() + " 픽업 완료.",
        "/orders/" + saved.getId());
    return orderMapper.toSellerResponse(saved);
  }

  /** 사장 미수령. READY → NO_SHOW. */
  @Transactional
  public SellerOrderResponse noShowOrder(Long sellerId, Long orderId) {
    Order order = findOrderForSeller(sellerId, orderId);
    // 상태 전이 가능 여부 확인
    if (!order.isReady()) {
      throw new BusinessException(OrderErrorCode.INVALID_ORDER_TRANSITION);
    }
    // 상태 전이
    order.noShow();
    Order saved = orderRepository.save(order);
    return orderMapper.toSellerResponse(saved);
  }

  /** 결제 시 차감된 쿠폰/포인트 복원 (취소·거절 공통). */
  private void restoreBenefits(Order order) {
    if (order.hasCoupon()) couponService.restore(order.getUserCouponId());
    if (order.hasUsedPoints()) pointService.restore(order, order.getPointUsed());
  }

  /** 결제 취소(환불) 헬퍼. Payment 가 있으면 PG 취소 → Payment.cancel() 저장. */
  private void refundPayment(Long orderId, String reason) {
    paymentRepository
        .findByOrderId(orderId)
        .ifPresent(
            payment -> {
              paymentGateway.cancel(
                  new PaymentCancellationCommand(
                      payment.getPaymentKey(), reason, payment.getAmount()));
              payment.cancel();
              paymentRepository.save(payment);
            });
  }

  /** 사장 전용: 주문 조회 + 본인 매장 검증. 공통 헬퍼. */
  private Order findOrderForSeller(Long sellerId, Long orderId) {
    Order order =
        orderRepository
            .findById(orderId)
            .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
    if (!order.getStore().isOwnedBy(sellerId)) {
      throw new BusinessException(OrderErrorCode.ORDER_FORBIDDEN);
    }
    return order;
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

  // ── AWAITING_PAYMENT 취소 + 재고복원 헬퍼 (createOrder / 만료배치 공용) ──────────────

  /** 고객의 기존 AWAITING_PAYMENT 주문을 모두 취소 + 재고복원. */
  private void cancelExistingAwaitingOrders(Long customerId) {
    List<Order> awaitingOrders =
        orderRepository.findByCustomerIdAndStatus(customerId, OrderStatus.AWAITING_PAYMENT);
    LocalDateTime now = LocalDateTime.now(clock);
    for (Order order : awaitingOrders) {
      cancelIfAwaitingPaymentOrder(order, now);
    }
  }

  /**
   * AWAITING_PAYMENT → CANCELLED 조건부 원자 전이 + DEAL 재고복원. affected=0(이미 전이됨)이면 no-op (멱등). confirm
   * 레이스는 TossConfirmService isAwaitingPayment 가드 + 이 조건부 UPDATE로 안전.
   */
  private void cancelIfAwaitingPaymentOrder(Order order, LocalDateTime now) {
    int updated = orderRepository.cancelIfAwaitingPayment(order.getId(), now);
    if (updated == 0) return;
    for (OrderItem item : order.getOrderItems()) {
      if (item.getItemKind() == ItemKind.DEAL && item.getClearanceItem() != null) {
        clearanceItemRepository.incrementStock(item.getClearanceItem().getId(), item.getQuantity());
      }
    }
  }

  // ── 동일 재시도 판정 헬퍼 (createOrder 재사용 분기용) ──────────────────────────────

  /**
   * 이번 요청과 모든 조건이 동일한 기존 AWAITING_PAYMENT 주문을 찾는다 (없으면 null). 동일하면 결제만 재시도하면 되므로 주문/재고를 재생성하지 않고
   * 그대로 재사용한다. 조회는 읽기 전용 — 재고/상태를 변경하지 않는다.
   */
  private Order findReusableAwaitingOrder(
      Long customerId,
      CreateOrderRequest request,
      List<ResolvedItem> resolvedItems,
      Long userCouponId,
      long pointUsed,
      LocalDateTime pickupTime,
      BigDecimal finalAmount) {
    for (Order existing :
        orderRepository.findByCustomerIdAndStatus(customerId, OrderStatus.AWAITING_PAYMENT)) {
      if (isSameOrderRequest(
          existing, request, resolvedItems, userCouponId, pointUsed, pickupTime, finalAmount)) {
        return existing;
      }
    }
    return null;
  }

  /**
   * 기존 주문이 이번 요청과 동일한지 판정 — 매장 / 품목 구성 / 쿠폰 / 포인트 / 픽업 / finalAmount 전부 만족해야 동일. 하나라도 다르면 false 를
   * 반환해 재생성 경로로 빠진다. finalAmount 비교 덕분에 재시도 사이 딜 가격이 바뀌면 자동으로 '다름'이 되어 freshness 가 유지된다.
   */
  private boolean isSameOrderRequest(
      Order existing,
      CreateOrderRequest request,
      List<ResolvedItem> resolvedItems,
      Long userCouponId,
      long pointUsed,
      LocalDateTime pickupTime,
      BigDecimal finalAmount) {
    // 같은 매장
    if (!existing.getStore().getId().equals(request.storeId())) return false;
    // 같은 쿠폰 (null 포함)
    if (!Objects.equals(existing.getUserCouponId(), userCouponId)) return false;
    // 같은 포인트 사용액 (미사용은 null 로 저장되므로 0 으로 정규화)
    long existingPointUsed = existing.getPointUsed() != null ? existing.getPointUsed() : 0L;
    if (existingPointUsed != pointUsed) return false;
    // 같은 픽업 (type + SLOT 시각)
    if (existing.getPickupType() != request.pickup().type()) return false;
    if (!Objects.equals(existing.getPickupTime(), pickupTime)) return false;
    // 같은 finalAmount (딜 가격 변동 시 달라져 자동 재생성)
    if (existing.getFinalAmount() == null
        || existing.getFinalAmount().compareTo(finalAmount) != 0) {
      return false;
    }
    // 같은 품목 구성 (상품/딜 id + 수량, 순서 무관)
    return itemSignature(resolvedItems).equals(orderItemSignature(existing));
  }

  /** 요청 항목의 품목 시그니처 — "kind:refId:qty" 정렬 목록 (순서 무관 multiset 비교용). */
  private static List<String> itemSignature(List<ResolvedItem> items) {
    return items.stream().map(OrderService::resolvedItemKey).sorted().toList();
  }

  /** 기존 주문 항목의 품목 시그니처 — itemSignature 와 동일 포맷. */
  private static List<String> orderItemSignature(Order order) {
    return order.getOrderItems().stream().map(OrderService::orderItemKey).sorted().toList();
  }

  private static String resolvedItemKey(ResolvedItem item) {
    Long refId =
        item.kind() == ItemKind.DEAL ? item.clearanceItem().getId() : item.product().getId();
    return item.kind() + ":" + refId + ":" + item.qty();
  }

  private static String orderItemKey(OrderItem item) {
    Long refId =
        item.getItemKind() == ItemKind.DEAL
            ? item.getClearanceItem().getId()
            : item.getProduct().getId();
    return item.getItemKind() + ":" + refId + ":" + item.getQuantity();
  }

  // ── 만료 배치 스케줄러 위임 메서드 ──────────────────────────────────────────────

  /**
   * 만료된 AWAITING_PAYMENT 주문 ID 목록 반환. TTL = {@value AWAITING_PAYMENT_TIMEOUT_MINUTES}분.
   *
   * @return 만료된 주문 ID 목록
   */
  public List<Long> findExpiredAwaitingOrderIds() {
    LocalDateTime cutoff = LocalDateTime.now(clock).minusMinutes(AWAITING_PAYMENT_TIMEOUT_MINUTES);
    return orderRepository.findExpiredAwaitingOrderIds(cutoff);
  }

  /**
   * 단건 만료 AWAITING 주문 취소 + 재고복원. 스케줄러에서 건별 독립 트랜잭션으로 호출.
   *
   * @param orderId 취소 대상 주문 ID
   */
  @Transactional
  public void cancelExpiredAwaitingOrder(Long orderId) {
    orderRepository
        .findByIdWithOrderItems(orderId)
        .ifPresent(order -> cancelIfAwaitingPaymentOrder(order, LocalDateTime.now(clock)));
  }

  /** AWAITING_PAYMENT 주문 자동취소 TTL (분). */
  static final int AWAITING_PAYMENT_TIMEOUT_MINUTES = 30;

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
