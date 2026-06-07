package com.magampick.store.repository;

/**
 * 지도 기반 매장 조회 PostGIS 후보 쿼리 projection. 위경도(latitude/longitude) 와 거리(distanceMeters) 를 함께 반환. 네이티브
 * 쿼리 alias 와 getter 명이 대소문자 무관 매핑된다 (Spring Data JPA 컨벤션).
 */
public interface MapStoreCandidate {

  Long getId();

  String getName();

  String getImageUrl();

  /** ST_Y(location::geometry) — 위도 (latitude). */
  Double getLatitude();

  /** ST_X(location::geometry) — 경도 (longitude). */
  Double getLongitude();

  /** ST_Distance 결과 — 미터 단위. distanceKm = distanceMeters / 1000 (서비스에서 변환). */
  Double getDistanceMeters();
}
