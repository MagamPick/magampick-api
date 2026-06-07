package com.magampick.order.mapper;

import com.magampick.order.domain.Order;
import com.magampick.order.domain.OrderItem;
import com.magampick.order.domain.PickupType;
import com.magampick.order.dto.OrderResponse;
import com.magampick.order.dto.SellerOrderResponse;
import com.magampick.refund.dto.RefundInfoResponse;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

/**
 * Order 엔티티 → OrderResponse / SellerOrderResponse DTO 변환. orderNo, pickup, amounts, createdAt 는 파생
 * 로직 포함.
 */
@Mapper(componentModel = "spring")
public interface OrderMapper {

  @Mapping(target = "orderNo", source = "id", qualifiedByName = "idToOrderNo")
  @Mapping(target = "storeId", source = "store.id")
  @Mapping(target = "storeName", source = "store.name")
  @Mapping(target = "storePhone", source = "store.phone")
  @Mapping(target = "items", source = "orderItems")
  @Mapping(target = "pickup", expression = "java(toPickupResponse(order))")
  @Mapping(target = "amounts", expression = "java(toAmountsResponse(order))")
  @Mapping(target = "pickupCode", source = "pickupCode")
  @Mapping(target = "status", source = "status")
  @Mapping(target = "paymentMethod", constant = "toss")
  @Mapping(target = "createdAt", source = "createdAt", qualifiedByName = "toKst")
  @Mapping(target = "completedAt", source = "completedAt", qualifiedByName = "toKst")
  @Mapping(target = "cancelledAt", source = "cancelledAt", qualifiedByName = "toKst")
  @Mapping(target = "refund", ignore = true)
  OrderResponse toResponse(Order order);

  /** 환불 정보를 포함한 OrderResponse 생성. toResponse(Order) 결과에 refund 만 교체한 새 레코드를 반환한다. */
  default OrderResponse withRefund(OrderResponse base, RefundInfoResponse refund) {
    return new OrderResponse(
        base.id(),
        base.orderNo(),
        base.storeId(),
        base.storeName(),
        base.storePhone(),
        base.items(),
        base.pickup(),
        base.memo(),
        base.amounts(),
        base.pickupCode(),
        base.status(),
        base.paymentMethod(),
        base.createdAt(),
        base.completedAt(),
        base.cancelledAt(),
        refund);
  }

  @Mapping(target = "orderNo", source = "id", qualifiedByName = "idToOrderNo")
  @Mapping(target = "storeId", source = "store.id")
  @Mapping(target = "storeName", source = "store.name")
  @Mapping(target = "storePhone", source = "store.phone")
  @Mapping(target = "items", source = "orderItems")
  @Mapping(target = "pickup", expression = "java(toPickupResponse(order))")
  @Mapping(target = "amounts", expression = "java(toAmountsResponse(order))")
  @Mapping(target = "pickupCode", source = "pickupCode")
  @Mapping(target = "status", source = "status")
  @Mapping(target = "paymentMethod", constant = "toss")
  @Mapping(target = "createdAt", source = "createdAt", qualifiedByName = "toKst")
  @Mapping(target = "customerName", source = "customer.nickname")
  @Mapping(target = "customerPhone", source = "customer.phone")
  @Mapping(target = "acceptedAt", source = "acceptedAt", qualifiedByName = "toKst")
  @Mapping(target = "readyAt", source = "readyAt", qualifiedByName = "toKst")
  @Mapping(target = "completedAt", source = "completedAt", qualifiedByName = "toKst")
  @Mapping(target = "rejectedAt", source = "rejectedAt", qualifiedByName = "toKst")
  @Mapping(target = "cancelledAt", source = "cancelledAt", qualifiedByName = "toKst")
  SellerOrderResponse toSellerResponse(Order order);

  @Mapping(
      target = "kind",
      expression = "java(item.getItemKind() != null ? item.getItemKind().name() : null)")
  @Mapping(target = "name", source = "name")
  @Mapping(target = "imageUrl", source = "imageUrl")
  @Mapping(target = "originalPrice", source = "originalPrice")
  @Mapping(target = "salePrice", source = "unitPrice")
  @Mapping(target = "qty", source = "quantity")
  OrderResponse.OrderItemResponse toItemResponse(OrderItem item);

  @Named("idToOrderNo")
  default String idToOrderNo(Long id) {
    if (id == null) return null;
    return String.format("%04d", id);
  }

  @Named("toKst")
  default OffsetDateTime toKst(LocalDateTime ldt) {
    return ldt == null ? null : ldt.atOffset(ZoneOffset.ofHours(9));
  }

  default OrderResponse.PickupResponse toPickupResponse(Order order) {
    if (order.getPickupType() == null || order.getPickupType() == PickupType.ASAP) {
      return new OrderResponse.PickupResponse("ASAP", null);
    }
    String timeStr = null;
    if (order.getPickupTime() != null) {
      timeStr = order.getPickupTime().format(DateTimeFormatter.ofPattern("HH:mm"));
    }
    return new OrderResponse.PickupResponse("SLOT", timeStr);
  }

  default OrderResponse.OrderAmountsResponse toAmountsResponse(Order order) {
    return new OrderResponse.OrderAmountsResponse(
        order.getNormalTotal(), order.getDiscountTotal(), order.getTotalPrice());
  }
}
