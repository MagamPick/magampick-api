package com.magampick.product.domain;

import com.magampick.global.common.BaseEntity;
import com.magampick.store.domain.Store;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "store_id", nullable = false)
  private Store store;

  @Column(name = "name", nullable = false, length = 50)
  private String name;

  @Column(name = "regular_price", nullable = false, precision = 12, scale = 0)
  private BigDecimal regularPrice;

  @Column(name = "image_url", length = 500)
  private String imageUrl;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 10)
  private ProductStatus status;

  @Enumerated(EnumType.STRING)
  @Column(name = "category", nullable = false, length = 20)
  private ProductCategory category;

  @Column(name = "description", length = 500)
  private String description;

  @Column(name = "deleted_at")
  private LocalDateTime deletedAt;

  @Builder
  private Product(
      Store store,
      String name,
      BigDecimal regularPrice,
      String imageUrl,
      ProductStatus status,
      ProductCategory category,
      String description) {
    this.store = store;
    this.name = name;
    this.regularPrice = regularPrice;
    this.imageUrl = imageUrl;
    this.status = status;
    // 기존 등록 경로(category 미전달)는 ETC 기본값 적용
    this.category = category != null ? category : ProductCategory.ETC;
    this.description = description;
  }

  public void updateInfo(
      String name,
      BigDecimal regularPrice,
      String imageUrl,
      String description,
      ProductCategory category,
      ProductStatus status) {
    if (name != null) this.name = name;
    if (regularPrice != null) this.regularPrice = regularPrice;
    if (imageUrl != null) this.imageUrl = imageUrl;
    if (description != null) this.description = description;
    if (category != null) this.category = category;
    if (status != null) this.status = status;
  }

  public void softDelete() {
    this.deletedAt = LocalDateTime.now();
  }

  public void markSoldOut() {
    if (this.status == ProductStatus.SOLD_OUT) return;
    this.status = ProductStatus.SOLD_OUT;
  }

  public void restock() {
    if (this.status == ProductStatus.ON_SALE) return;
    this.status = ProductStatus.ON_SALE;
  }
}
