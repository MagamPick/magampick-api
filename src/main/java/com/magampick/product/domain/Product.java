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

  @Builder
  private Product(
      Store store, String name, BigDecimal regularPrice, String imageUrl, ProductStatus status) {
    this.store = store;
    this.name = name;
    this.regularPrice = regularPrice;
    this.imageUrl = imageUrl;
    this.status = status;
  }
}
