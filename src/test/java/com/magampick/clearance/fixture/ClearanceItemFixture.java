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
import java.time.ZoneOffset;

public class ClearanceItemFixture {

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
    return new ClearanceItemCreateRequest(
        productId, new BigDecimal("3000"), 5, todayAt(17, 0), todayAt(21, 0));
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

  private static LocalDateTime todayAt(int hour, int minute) {
    return LocalDate.now().atTime(hour, minute);
  }
}
