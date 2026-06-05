package com.magampick.store.service;

import org.locationtech.jts.geom.Point;

/**
 * 지오코딩. 자체 DB(geocode_buildings, 위치정보요약DB 적재) 기반 — ADR-002 / ADR-003.
 *
 * <ul>
 *   <li>정방향(주소 → 좌표): 다음 위젯 결과로 도로명 자연키를 조립해 정확 매칭.
 *   <li>역방향(좌표 → 도로명 라벨): PostGIS 최근접(KNN). GPS 주소지 라벨 채움용.
 * </ul>
 */
public interface GeocodingService {

  /** 정방향. 매칭 실패 시 {@code ADDRESS_GEOCODING_FAILED} 를 던진다. */
  Point geocode(GeocodeQuery query);

  /** 역방향. 최근접 건물의 도로명주소 라벨 (매칭 없으면 {@code null}). */
  String reverseGeocode(Point point);
}
