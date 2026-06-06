package com.magampick.store.repository;

/**
 * 소비자 매장 목록 PostGIS 후보 쿼리 projection. 네이티브 쿼리 alias 와 getter 명이 대소문자 무관 매핑된다 (Spring Data JPA 컨벤션).
 */
public interface StoreCandidate {

  Long getId();

  String getName();

  String getImageUrl();

  /** ST_Distance 결과 — 미터 단위. distanceKm = distanceMeters / 1000 (서비스에서 변환). */
  Double getDistanceMeters();
}
