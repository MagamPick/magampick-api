package com.magampick.product.domain;

/** 상품 카테고리. 메뉴 탭 조회 시 FE 그룹화에 사용. 기본값: ETC. */
public enum ProductCategory {
  BAKERY("베이커리"),
  BEVERAGE("음료"),
  DESSERT("디저트"),
  ETC("기타");

  private final String label;

  ProductCategory(String label) {
    this.label = label;
  }

  public String getLabel() {
    return label;
  }
}
