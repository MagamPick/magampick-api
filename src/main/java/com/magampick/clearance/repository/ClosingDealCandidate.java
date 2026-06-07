package com.magampick.clearance.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 마감 임박 특가 네이티브 쿼리 projection. {@link ClearanceItemRepository#findClosingSoonDeals} 결과 매핑에 사용. */
public interface ClosingDealCandidate {

  Long getId();

  String getStoreName();

  String getProductName();

  /** products.image_url (nullable). product 없는 떨이는 null. */
  String getImageUrl();

  BigDecimal getRegularPrice();

  BigDecimal getSalePrice();

  LocalDateTime getPickupDeadline();
}
