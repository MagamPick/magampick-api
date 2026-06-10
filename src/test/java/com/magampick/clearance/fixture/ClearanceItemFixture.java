package com.magampick.clearance.fixture;

import com.magampick.clearance.domain.ClearanceItem;
import com.magampick.clearance.domain.ClearanceItemStatus;
import com.magampick.clearance.dto.ClearanceItemCreateRequest;
import com.magampick.clearance.dto.ClearanceItemResponse;
import com.magampick.product.domain.Product;
import com.magampick.store.domain.Store;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

public class ClearanceItemFixture {

  // ClearanceItemService 가 픽업 시각을 KST 기준으로 검증 → fixture 의 todayAt 도 동일 기준이어야 자정 직후 CI 가 실패 안 한다
  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private ClearanceItemFixture() {}

  public static ClearanceItem aClearanceItem(Store store, Product product) {
    return ClearanceItem.builder()
        .store(store)
        .product(product)
        .name("크로아상")
        .regularPrice(new BigDecimal("4500"))
        .salePrice(new BigDecimal("3000"))
        .totalQuantity(5)
        .pickupStartAt(todayAt(17, 0))
        .pickupEndAt(todayAt(21, 0))
        .build();
  }

  public static ClearanceItemCreateRequest aCreateRequest(Long productId) {
    return new ClearanceItemCreateRequest(productId, new BigDecimal("3000"), 5, todayAt(23, 59));
  }

  public static ClearanceItemResponse aResponse(Long id) {
    return new ClearanceItemResponse(
        id,
        1L,
        "/uploads/2026/5/product.jpg",
        "크로아상",
        new BigDecimal("4500"),
        new BigDecimal("3000"),
        new BigDecimal("0.33"),
        5,
        5,
        OffsetDateTime.of(todayAt(17, 0), ZoneOffset.ofHours(9)),
        OffsetDateTime.of(todayAt(21, 0), ZoneOffset.ofHours(9)),
        ClearanceItemStatus.OPEN,
        OffsetDateTime.now(ZoneOffset.ofHours(9)));
  }

  public static ClearanceItemResponse aClosedResponse(Long id) {
    return new ClearanceItemResponse(
        id,
        1L,
        "/uploads/2026/5/product.jpg",
        "크로아상",
        new BigDecimal("4500"),
        new BigDecimal("3000"),
        new BigDecimal("0.33"),
        5,
        5,
        OffsetDateTime.of(todayAt(17, 0), ZoneOffset.ofHours(9)),
        OffsetDateTime.of(todayAt(21, 0), ZoneOffset.ofHours(9)),
        ClearanceItemStatus.CLOSED,
        OffsetDateTime.now(ZoneOffset.ofHours(9)));
  }

  private static LocalDateTime todayAt(int hour, int minute) {
    return LocalDate.now(KST).atTime(hour, minute);
  }
}
