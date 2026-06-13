package com.magampick.refund.mapper;

import com.magampick.order.domain.Order;
import com.magampick.order.domain.OrderItem;
import com.magampick.refund.domain.Refund;
import com.magampick.refund.dto.RefundInfoResponse;
import com.magampick.refund.dto.RefundItemResponse;
import com.magampick.refund.dto.RefundResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

/** Refund 엔티티 → DTO 변환. */
@Mapper(componentModel = "spring")
public interface RefundMapper {

  /** Refund → OrderResponse 내부 환불 sub-field 변환. */
  @Mapping(target = "status", expression = "java(refund.getStatus().name())")
  @Mapping(target = "requestedAt", source = "requestedAt", qualifiedByName = "toKst")
  @Mapping(target = "resolvedAt", source = "resolvedAt", qualifiedByName = "toKst")
  RefundInfoResponse toInfoResponse(Refund refund);

  /** Refund → 사장 뷰 RefundResponse 변환. */
  @Mapping(target = "orderId", source = "order.id")
  @Mapping(target = "orderNo", source = "order.id", qualifiedByName = "idToOrderNo")
  @Mapping(target = "storeId", source = "order.store.id")
  @Mapping(target = "customerName", source = "order.customer.nickname")
  @Mapping(target = "items", expression = "java(mapItems(refund))")
  @Mapping(target = "amount", expression = "java(refundCashAmount(refund.getOrder()))")
  @Mapping(target = "pickupCompletedAt", source = "order.completedAt", qualifiedByName = "toKst")
  @Mapping(target = "status", expression = "java(refund.getStatus().name())")
  @Mapping(target = "requestedAt", source = "requestedAt", qualifiedByName = "toKst")
  @Mapping(target = "resolvedAt", source = "resolvedAt", qualifiedByName = "toKst")
  RefundResponse toResponse(Refund refund);

  @Named("idToOrderNo")
  default String idToOrderNo(Long id) {
    if (id == null) return null;
    return String.format("%04d", id);
  }

  @Named("toKst")
  default OffsetDateTime toKst(LocalDateTime ldt) {
    return ldt == null ? null : ldt.atOffset(ZoneOffset.ofHours(9));
  }

  /**
   * 현금 환불액 = 실 결제 현금(finalAmount). 혜택 미적용 구주문 등 finalAmount 가 null 이면 totalPrice 로 폴백.
   *
   * <p>쿠폰·포인트 복원(reverseBenefits)은 별도로 수행되므로, 현금 환불을 totalPrice(혜택 전)로 잡으면 복원분과 합쳐져 과환불이 된다. 실 결제
   * 현금 기준으로 환불해야 복원분과 합쳐 정확히 totalPrice 만 고객에게 환원된다.
   */
  default BigDecimal refundCashAmount(Order order) {
    return order.getFinalAmount() != null ? order.getFinalAmount() : order.getTotalPrice();
  }

  default List<RefundItemResponse> mapItems(Refund refund) {
    return refund.getOrder().getOrderItems().stream()
        .map(
            item -> new RefundItemResponse(item.getName(), item.getQuantity(), item.getUnitPrice()))
        .toList();
  }

  default RefundItemResponse toItemResponse(OrderItem item) {
    return new RefundItemResponse(item.getName(), item.getQuantity(), item.getUnitPrice());
  }
}
