package com.magampick.search.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 검색 결과 상품 아이템 discriminated union. kind = "deal" | "menu". Jackson: EXISTING_PROPERTY 로 kind 필드를
 * 공용 판별자로 사용.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "kind")
@JsonSubTypes({
  @JsonSubTypes.Type(value = SearchProductItemResponse.DealSearchItem.class, name = "deal"),
  @JsonSubTypes.Type(value = SearchProductItemResponse.MenuSearchItem.class, name = "menu")
})
@Schema(
    description = "검색 결과 상품 아이템 (kind: deal | menu)",
    oneOf = {
      SearchProductItemResponse.DealSearchItem.class,
      SearchProductItemResponse.MenuSearchItem.class
    })
public sealed interface SearchProductItemResponse
    permits SearchProductItemResponse.DealSearchItem, SearchProductItemResponse.MenuSearchItem {

  String kind();

  /** 떨이(deal) 아이템. kind = "deal". */
  @Schema(description = "떨이 아이템")
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

  /** 일반 상품(menu) 아이템. kind = "menu". */
  @Schema(description = "메뉴 아이템")
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
