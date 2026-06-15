package com.magampick.favorite.repository;

import java.time.LocalDateTime;

/**
 * 단골 매장 목록 PostGIS 거리 쿼리 projection. 네이티브 쿼리 alias 와 getter 명이 대소문자 무관 매핑된다 (Spring Data JPA 컨벤션).
 */
public interface FavoriteStoreCandidate {

  Long getStoreId();

  String getName();

  String getImageUrl();

  /** ST_Distance 결과 — 미터 단위. distanceKm = distanceMeters / 1000 (서비스에서 변환). */
  Double getDistanceMeters();

  /** favorites.created_at — 정렬 기준(등록순 asc). */
  LocalDateTime getCreatedAt();
}
