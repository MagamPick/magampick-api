package com.magampick.clearance.repository;

/** 떨이 이름 자동완성 네이티브 쿼리 projection. word_similarity 점수 포함. */
public interface DealNameSuggestion {

  String getName();

  /** pg_trgm word_similarity 점수 (0.0 ~ 1.0). */
  Double getSimilarity();
}
