package com.magampick.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

@Schema(description = "매장 리뷰 목록 아이템")
public record StoreReviewResponse(
    @Schema(description = "리뷰 ID") Long id,
    @Schema(description = "작성자 닉네임") String authorNickname,
    @Schema(description = "별점 (1-5)") int rating,
    @Schema(description = "리뷰 내용") String content,
    @Schema(description = "작성 시각") OffsetDateTime createdAt,
    @Schema(description = "주문한 떨이 상품 목록") List<ReviewedProduct> products,
    @Schema(description = "리뷰 사진 URL 목록 (sort_order 오름차순)") List<String> photos,
    @Schema(description = "리뷰 태그 한국어 라벨 목록") List<String> tags,
    @Schema(description = "사장 답글 (없으면 null)") String ownerReply) {

  @Schema(description = "리뷰에 포함된 주문 떨이 상품")
  public record ReviewedProduct(
      @Schema(description = "떨이 상품 ID") Long productId,
      @Schema(description = "상품 종류 (항상 'deal')", example = "deal") String kind,
      @Schema(description = "상품명") String name) {}
}
