package com.magampick.order.service;

import com.magampick.clearance.domain.ClearanceItem;
import com.magampick.clearance.domain.ClearanceItemStatus;
import com.magampick.clearance.exception.ClearanceItemErrorCode;
import com.magampick.clearance.repository.ClearanceItemRepository;
import com.magampick.customer.domain.Customer;
import com.magampick.customer.exception.CustomerErrorCode;
import com.magampick.customer.repository.CustomerRepository;
import com.magampick.global.exception.BusinessException;
import com.magampick.order.domain.Order;
import com.magampick.order.domain.OrderItem;
import com.magampick.order.domain.OrderStatus;
import com.magampick.order.domain.PickupType;
import com.magampick.order.dto.DevSeedOrderRequest;
import com.magampick.order.dto.DevSeedOrderResponse;
import com.magampick.order.exception.OrderErrorCode;
import com.magampick.order.repository.OrderRepository;
import com.magampick.payment.domain.Payment;
import com.magampick.payment.domain.PaymentStatus;
import com.magampick.payment.repository.PaymentRepository;
import com.magampick.store.domain.Store;
import com.magampick.store.exception.StoreErrorCode;
import com.magampick.store.repository.StoreRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** E2E 테스트용 주문 시드 서비스 — local/dev 프로파일 한정. 토스 결제 우회 후 targetState 까지 전이. */
@Service
@Profile({"local", "dev"})
@RequiredArgsConstructor
@Transactional
public class DevOrderSeedService {

  private static final Set<OrderStatus> ALLOWED_TARGET_STATES =
      EnumSet.of(
          OrderStatus.PENDING,
          OrderStatus.PREPARING,
          OrderStatus.READY,
          OrderStatus.COMPLETED,
          OrderStatus.NO_SHOW);

  private final CustomerRepository customerRepository;
  private final StoreRepository storeRepository;
  private final ClearanceItemRepository clearanceItemRepository;
  private final OrderRepository orderRepository;
  private final PaymentRepository paymentRepository;
  private final Clock clock;

  public DevSeedOrderResponse seedOrder(DevSeedOrderRequest request) {
    if (!ALLOWED_TARGET_STATES.contains(request.targetState())) {
      throw new BusinessException(OrderErrorCode.INVALID_ORDER_TRANSITION);
    }

    LocalDateTime now = LocalDateTime.now(clock);

    // 엔티티 조회 및 검증 (decrementStock 이전)
    Customer customer = resolveCustomer(request.customerId());
    Long customerId = customer.getId();

    Store store = resolveStore(request.storeId());
    Long storeId = store.getId();

    ClearanceItem item = resolveClearanceItem(request.clearanceItemId(), storeId, now);
    Long itemId = item.getId();
    BigDecimal regular = item.getRegularPrice();
    BigDecimal sale = item.getSalePrice();
    String itemName = item.getName();
    String imageUrl = item.getProduct() != null ? item.getProduct().getImageUrl() : null;

    // 재고 차감 (@Modifying clearAutomatically=true → EntityManager 초기화)
    int updated = clearanceItemRepository.decrementStock(itemId, 1);
    if (updated == 0) {
      throw new BusinessException(ClearanceItemErrorCode.OUT_OF_STOCK);
    }

    // EntityManager 초기화 후 프록시로 재참조
    Customer customerRef = customerRepository.getReferenceById(customerId);
    Store storeRef = storeRepository.getReferenceById(storeId);
    ClearanceItem itemRef = clearanceItemRepository.getReferenceById(itemId);

    BigDecimal payTotal = sale;
    BigDecimal discountTotal = regular.subtract(sale);
    String pickupCode = String.format("%04d", ThreadLocalRandom.current().nextInt(10000));

    Order order =
        Order.builder()
            .customer(customerRef)
            .store(storeRef)
            .status(OrderStatus.AWAITING_PAYMENT)
            .totalPrice(payTotal)
            .pickupType(PickupType.ASAP)
            .pickupCode(pickupCode)
            .normalTotal(regular)
            .discountTotal(discountTotal)
            .finalAmount(payTotal)
            .build();

    order.addOrderItem(OrderItem.forDeal(order, itemRef, itemName, regular, imageUrl, 1, sale));

    // 토스 결제 우회 — AWAITING_PAYMENT → PENDING
    order.activate();
    Order saved = orderRepository.save(order);

    // 가짜 결제 레코드 저장
    Payment payment =
        Payment.builder()
            .order(saved)
            .provider("DEV_SEED")
            .method("dev")
            .paymentKey("dev-seed-" + saved.getId())
            .amount(payTotal)
            .status(PaymentStatus.APPROVED)
            .approvedAt(now)
            .build();
    paymentRepository.save(payment);

    // targetState 까지 전이 (기존 도메인 메서드 재사용)
    applyTransitions(saved, request.targetState(), now);
    orderRepository.save(saved);

    return new DevSeedOrderResponse(
        saved.getId(),
        String.format("%04d", saved.getId()),
        saved.getPickupCode(),
        saved.getStatus().name(),
        customerId,
        storeId,
        payTotal);
  }

  private void applyTransitions(Order order, OrderStatus target, LocalDateTime now) {
    if (target == OrderStatus.PENDING) return;
    order.accept(now);
    if (target == OrderStatus.PREPARING) return;
    order.markReady(now);
    if (target == OrderStatus.READY) return;
    if (target == OrderStatus.COMPLETED) {
      order.complete(now);
    } else {
      order.noShow();
    }
  }

  private Customer resolveCustomer(Long customerId) {
    if (customerId != null) {
      return customerRepository
          .findById(customerId)
          .orElseThrow(() -> new BusinessException(CustomerErrorCode.CUSTOMER_NOT_FOUND));
    }
    return customerRepository.findAll(PageRequest.of(0, 1)).stream()
        .findFirst()
        .orElseThrow(() -> new BusinessException(CustomerErrorCode.CUSTOMER_NOT_FOUND));
  }

  private Store resolveStore(Long storeId) {
    if (storeId != null) {
      Store store =
          storeRepository
              .findByIdAndDeletedAtIsNull(storeId)
              .orElseThrow(() -> new BusinessException(StoreErrorCode.STORE_NOT_FOUND));
      if (!store.isOpen()) throw new BusinessException(StoreErrorCode.STORE_CLOSED);
      return store;
    }
    return storeRepository.findAll().stream()
        .filter(s -> s.getDeletedAt() == null && s.isOpen())
        .findFirst()
        .orElseThrow(() -> new BusinessException(StoreErrorCode.STORE_NOT_FOUND));
  }

  private ClearanceItem resolveClearanceItem(
      Long clearanceItemId, Long storeId, LocalDateTime now) {
    if (clearanceItemId != null) {
      ClearanceItem ci =
          clearanceItemRepository
              .findByIdWithStoreAndProduct(clearanceItemId)
              .orElseThrow(
                  () -> new BusinessException(ClearanceItemErrorCode.CLEARANCE_ITEM_NOT_FOUND));
      if (!ci.isOpen() || now.isAfter(ci.getPickupEndAt())) {
        throw new BusinessException(ClearanceItemErrorCode.CLEARANCE_CLOSED);
      }
      return ci;
    }
    List<ClearanceItem> items =
        clearanceItemRepository.findByStoreIdAndStatus(storeId, ClearanceItemStatus.OPEN);
    return items.stream()
        .filter(ci -> !now.isAfter(ci.getPickupEndAt()))
        .findFirst()
        .orElseThrow(() -> new BusinessException(ClearanceItemErrorCode.CLEARANCE_ITEM_NOT_FOUND));
  }
}
