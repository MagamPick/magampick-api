package com.magampick.product.fixture;

import com.magampick.product.domain.Product;
import com.magampick.product.domain.ProductStatus;
import com.magampick.product.dto.ProductCreateRequest;
import com.magampick.product.dto.ProductResponse;
import com.magampick.product.dto.ProductUpdateRequest;
import com.magampick.store.domain.Store;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public class ProductFixture {

  private ProductFixture() {}

  public static Product aProduct(Store store) {
    return Product.builder()
        .store(store)
        .name("크로아상")
        .regularPrice(new BigDecimal("4500"))
        .imageUrl("/uploads/2026/5/product.jpg")
        .status(ProductStatus.ON_SALE)
        .build();
  }

  public static Product aSoldOutProduct(Store store) {
    return Product.builder()
        .store(store)
        .name("크로아상")
        .regularPrice(new BigDecimal("4500"))
        .imageUrl("/uploads/2026/5/product.jpg")
        .status(ProductStatus.SOLD_OUT)
        .build();
  }

  public static ProductCreateRequest aCreateRequest() {
    return new ProductCreateRequest("크로아상", new BigDecimal("4500"));
  }

  public static ProductUpdateRequest aUpdateRequest() {
    return new ProductUpdateRequest("바게트", new BigDecimal("5000"));
  }

  public static ProductResponse aResponse(Long id) {
    return new ProductResponse(
        id,
        "크로아상",
        new BigDecimal("4500"),
        "/uploads/2026/5/product.jpg",
        ProductStatus.ON_SALE,
        OffsetDateTime.now());
  }

  public static ProductResponse aResponseWithoutImage(Long id) {
    return new ProductResponse(
        id, "크로아상", new BigDecimal("4500"), null, ProductStatus.ON_SALE, OffsetDateTime.now());
  }

  public static ProductResponse aResponseWithStatus(Long id, ProductStatus status) {
    return new ProductResponse(
        id,
        "크로아상",
        new BigDecimal("4500"),
        "/uploads/2026/5/product.jpg",
        status,
        OffsetDateTime.now());
  }
}
