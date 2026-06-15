package com.magampick.clearance.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 검색용 떨이 목록 네이티브 쿼리 projection. {@link ClearanceItemRepository#searchOpenDealsByStoreIds} 결과 매핑에
 * 사용.
 */
public interface DealSearchCandidate {

  Long getId();

  Long getStoreId();

  String getName();

  /** products.image_url (nullable). product 없는 떨이는 null. */
  String getImageUrl();

  BigDecimal getRegularPrice();

  BigDecimal getSalePrice();

  LocalDateTime getPickupEndAt();
}
