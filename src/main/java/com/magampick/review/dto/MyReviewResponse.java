package com.magampick.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

@Schema(description = "소비자 본인 리뷰 아이템")
public record MyReviewResponse(
    @Schema(description = "리뷰 ID") Long id,
    @Schema(description = "매장 ID") Long storeId,
    @Schema(description = "매장명") String storeName,
    @Schema(description = "주문한 상품 목록") List<ReviewedProduct> items,
    @Schema(description = "별점 (1-5)") int rating,
    @Schema(description = "리뷰 내용") String content,
    @Schema(description = "리뷰 태그 한국어 라벨 목록") List<String> tags,
    @Schema(description = "리뷰 사진 URL 목록") List<String> photos,
    @Schema(description = "작성 시각") OffsetDateTime createdAt,
    @Schema(description = "사장 답글 (없으면 null)") String ownerReply) {

  @Schema(description = "리뷰에 포함된 주문 상품")
  public record ReviewedProduct(
      @Schema(description = "상품 ID") Long productId,
      @Schema(description = "상품 종류 (deal / menu)") String kind,
      @Schema(description = "상품명") String name) {}
}
