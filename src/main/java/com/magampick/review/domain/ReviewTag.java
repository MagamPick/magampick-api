package com.magampick.review.domain;

/** 리뷰 태그 고정 프리셋 5종. Phase 7 (리뷰 작성) 에서 확정/확장 예정. 응답 시 {@link #getLabel()} 한국어 라벨로 변환. */
public enum ReviewTag {
  FRESH("신선해요"),
  KIND("친절해요"),
  REVISIT("재방문"),
  GENEROUS("양 많아요"),
  GOOD_VALUE("가성비 좋아요");

  private final String label;

  ReviewTag(String label) {
    this.label = label;
  }

  public String getLabel() {
    return label;
  }
}
