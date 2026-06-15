package com.magampick.review.service;

/** 평점 집계 결과. Phase 4 탐색 기능들이 {@link ReviewQueryService} 주입 시 사용. 0건이면 average=0.0, count=0. */
public record RatingStats(double average, long count) {

  public static final RatingStats EMPTY = new RatingStats(0.0, 0L);
}
