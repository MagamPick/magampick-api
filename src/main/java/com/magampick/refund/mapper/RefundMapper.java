package com.magampick.refund.mapper;

import com.magampick.order.domain.OrderItem;
import com.magampick.refund.domain.Refund;
import com.magampick.refund.dto.RefundInfoResponse;
import com.magampick.refund.dto.RefundItemResponse;
import com.magampick.refund.dto.RefundResponse;
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
  @Mapping(target = "amount", source = "order.totalPrice")
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
