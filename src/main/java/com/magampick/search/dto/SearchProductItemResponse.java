package com.magampick.search.dto;

import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 검색 결과 상품 아이템 discriminated union. kind = "deal" | "menu".
 *
 * <p>OpenAPI: 자식(Deal/Menu)은 공통 베이스({@link SearchProductItemBase})만 allOf 상속하고, 이 union 은 자식만 oneOf
 * 로 참조한다 — 부모↔자식 순환 참조 제거 (openapi-typescript TS2502 방지). 와이어 포맷은 각 record 가 {@code kind} 필드를 그대로
 * 직렬화하므로 변동 없음.
 */
@Schema(
    description = "검색 결과 상품 아이템 (kind: deal | menu)",
    oneOf = {
      SearchProductItemResponse.DealSearchItem.class,
      SearchProductItemResponse.MenuSearchItem.class
    },
    discriminatorProperty = "kind",
    discriminatorMapping = {
      @DiscriminatorMapping(
          value = "deal",
          schema = SearchProductItemResponse.DealSearchItem.class),
      @DiscriminatorMapping(value = "menu", schema = SearchProductItemResponse.MenuSearchItem.class)
    })
public sealed interface SearchProductItemResponse
    permits SearchProductItemResponse.DealSearchItem, SearchProductItemResponse.MenuSearchItem {

  String kind();

  /** 떨이(deal) 아이템. kind = "deal". 공통 필드는 {@link SearchProductItemBase} 참조. */
  @Schema(description = "떨이 아이템", allOf = SearchProductItemBase.class)
  record DealSearchItem(
      @Schema(description = "kind 판별자", example = "deal") String kind,
      @Schema(description = "떨이 ID") Long id,
      @Schema(description = "매장 ID") Long storeId,
      @Schema(description = "매장명") String storeName,
      @Schema(description = "상품명") String name,
      @Schema(description = "이미지 URL (nullable)") String imageUrl,
      @Schema(description = "정상가 (원)") java.math.BigDecimal originalPrice,
      @Schema(description = "판매가 (원)") java.math.BigDecimal salePrice,
      @Schema(description = "할인율 (%)", example = "40") int discountRate)
      implements SearchProductItemResponse {

    public DealSearchItem(
        Long id,
        Long storeId,
        String storeName,
        String name,
        String imageUrl,
        java.math.BigDecimal originalPrice,
        java.math.BigDecimal salePrice,
        int discountRate) {
      this("deal", id, storeId, storeName, name, imageUrl, originalPrice, salePrice, discountRate);
    }
  }

  /** 일반 상품(menu) 아이템. kind = "menu". 공통 필드는 {@link SearchProductItemBase} 참조. */
  @Schema(description = "메뉴 아이템", allOf = SearchProductItemBase.class)
  record MenuSearchItem(
      @Schema(description = "kind 판별자", example = "menu") String kind,
      @Schema(description = "상품 ID") Long id,
      @Schema(description = "매장 ID") Long storeId,
      @Schema(description = "매장명") String storeName,
      @Schema(description = "상품명") String name,
      @Schema(description = "이미지 URL (nullable)") String imageUrl,
      @Schema(description = "정상가 (원)") java.math.BigDecimal price)
      implements SearchProductItemResponse {

    public MenuSearchItem(
        Long id,
        Long storeId,
        String storeName,
        String name,
        String imageUrl,
        java.math.BigDecimal price) {
      this("menu", id, storeId, storeName, name, imageUrl, price);
    }
  }
}
