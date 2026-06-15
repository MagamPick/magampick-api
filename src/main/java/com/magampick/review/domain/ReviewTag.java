package com.magampick.review.domain;

/** 리뷰 태그 고정 프리셋 7종. 응답 시 {@link #getLabel()} 한국어 라벨로 변환. */
public enum ReviewTag {
  DELICIOUS("맛있어요"),
  FRESH("신선해요"),
  REORDER("재구매"),
  FAST_PICKUP("픽업 빨라요"),
  GENEROUS("양 많아요"),
  GOOD_VALUE("가성비"),
  KIND("친절해요");

  private final String label;

  ReviewTag(String label) {
    this.label = label;
  }

  public String getLabel() {
    return label;
  }
}
