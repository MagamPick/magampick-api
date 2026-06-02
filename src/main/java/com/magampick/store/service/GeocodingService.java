package com.magampick.store.service;

import org.locationtech.jts.geom.Point;

/** 도로명 주소 → 좌표 변환. 변환 실패 시 {@code ADDRESS_GEOCODING_FAILED} 를 던진다. */
public interface GeocodingService {

  Point geocode(String roadAddress);
}
