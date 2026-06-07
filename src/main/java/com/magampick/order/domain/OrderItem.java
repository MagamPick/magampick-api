package com.magampick.order.domain;

import com.magampick.clearance.domain.ClearanceItem;
import com.magampick.global.common.BaseEntity;
import com.magampick.product.domain.Product;
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

/**
 * 주문 항목. Phase 5A: DEAL(떨이) + MENU(일반 상품) 혼합 지원. 스냅샷 필드(name, originalPrice, imageUrl)로 상품 조인 없이 표시
 * 가능. FK clearanceItem / product 는 정확히 하나만 NOT NULL (DB CHECK 보장).
 */
@Entity
@Table(name = "order_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "order_id", nullable = false)
  private Order order;

  /** DEAL 항목. MENU 항목은 null. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "clearance_item_id")
  private ClearanceItem clearanceItem;

  /** MENU 항목. DEAL 항목은 null. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "product_id")
  private Product product;

  /** 항목 종류 (DEAL / MENU). */
  @Enumerated(EnumType.STRING)
  @Column(name = "item_kind", length = 10)
  private ItemKind itemKind;

  /** 상품명 스냅샷 — 조인 없이 표시. */
  @Column(name = "name", length = 255)
  private String name;

  /** 정상가 스냅샷 (DEAL = regularPrice, MENU = regularPrice). */
  @Column(name = "original_price", precision = 12, scale = 0)
  private BigDecimal originalPrice;

  /** 이미지 URL 스냅샷 (nullable). */
  @Column(name = "image_url", length = 500)
  private String imageUrl;

  /** 결제 단가 (DEAL = salePrice, MENU = regularPrice). */
  @Column(name = "unit_price", nullable = false, precision = 12, scale = 0)
  private BigDecimal unitPrice;

  @Column(name = "quantity", nullable = false)
  private int quantity;

  @Column(name = "subtotal", nullable = false, precision = 12, scale = 0)
  private BigDecimal subtotal;

  @Builder
  private OrderItem(
      Order order,
      ClearanceItem clearanceItem,
      Product product,
      ItemKind itemKind,
      String name,
      BigDecimal originalPrice,
      String imageUrl,
      int quantity,
      BigDecimal unitPrice) {
    this.order = order;
    this.clearanceItem = clearanceItem;
    this.product = product;
    this.itemKind = itemKind;
    this.name = name;
    this.originalPrice = originalPrice;
    this.imageUrl = imageUrl;
    this.quantity = quantity;
    this.unitPrice = unitPrice;
    this.subtotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
  }

  /** DEAL 항목 생성 팩토리. */
  public static OrderItem forDeal(
      Order order,
      ClearanceItem clearanceItem,
      String name,
      BigDecimal originalPrice,
      String imageUrl,
      int quantity,
      BigDecimal unitPrice) {
    return OrderItem.builder()
        .order(order)
        .clearanceItem(clearanceItem)
        .itemKind(ItemKind.DEAL)
        .name(name)
        .originalPrice(originalPrice)
        .imageUrl(imageUrl)
        .quantity(quantity)
        .unitPrice(unitPrice)
        .build();
  }

  /** MENU 항목 생성 팩토리. */
  public static OrderItem forMenu(
      Order order,
      Product product,
      String name,
      BigDecimal originalPrice,
      String imageUrl,
      int quantity,
      BigDecimal unitPrice) {
    return OrderItem.builder()
        .order(order)
        .product(product)
        .itemKind(ItemKind.MENU)
        .name(name)
        .originalPrice(originalPrice)
        .imageUrl(imageUrl)
        .quantity(quantity)
        .unitPrice(unitPrice)
        .build();
  }
}
