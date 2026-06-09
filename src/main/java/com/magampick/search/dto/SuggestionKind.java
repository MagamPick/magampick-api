package com.magampick.search.dto;

import com.fasterxml.jackson.annotation.JsonValue;

/** 자동완성 제안 아이템 종류. Jackson 직렬화: lowercase ("store" / "product"). */
public enum SuggestionKind {
  STORE("store"),
  PRODUCT("product");

  private final String value;

  SuggestionKind(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }
}
