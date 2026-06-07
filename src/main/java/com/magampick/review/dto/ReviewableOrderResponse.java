package com.magampick.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

@Schema(description = "리뷰 작성 가능한 완료 주문 아이템")
public record ReviewableOrderResponse(
    @Schema(description = "주문 ID") Long orderId,
    @Schema(description = "매장 ID") Long storeId,
    @Schema(description = "매장명") String storeName,
    @Schema(description = "주문 상품 목록") List<OrderedItem> items,
    @Schema(description = "픽업 완료 시각") OffsetDateTime pickedUpAt,
    @Schema(description = "리뷰 작성 여부") boolean reviewed,
    @Schema(description = "리뷰 ID (리뷰 없으면 null)") Long reviewId) {

  @Schema(description = "주문 상품 아이템")
  public record OrderedItem(
      @Schema(description = "상품 ID") Long productId,
      @Schema(description = "상품 종류 (deal / menu)") String kind,
      @Schema(description = "상품명") String name) {}
}
