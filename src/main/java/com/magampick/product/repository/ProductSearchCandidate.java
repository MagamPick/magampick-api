package com.magampick.product.repository;

import java.math.BigDecimal;

/**
 * 검색용 상품 목록 네이티브 쿼리 projection. {@link ProductRepository#searchOnSaleProductsByStoreIds} 결과 매핑에 사용.
 */
public interface ProductSearchCandidate {

  Long getId();

  Long getStoreId();

  String getName();

  /** products.image_url (nullable). */
  String getImageUrl();

  BigDecimal getRegularPrice();
}
