package com.magampick.store.service;

import com.magampick.global.common.GeometryUtil;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Point;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * 지오코딩 Mock. 카카오 로컬 API 실연동 전까지 사용한다. 도로명 주소 문자열의 해시로 서울 bounding box 내 결정적 좌표를 생성해 시연 시 매장이 한 점에
 * 몰리지 않게 한다. 실연동(주소 검증·정확 좌표·서비스 영역 외 거부)은 후속 작업.
 */
@Slf4j
@Service
@Profile("!prod")
public class MockGeocodingService implements GeocodingService {

  // 서울 대략 bounding box: 위도 37.43~37.70, 경도 126.80~127.18
  private static final double LAT_MIN = 37.43;
  private static final double LAT_SPAN = 0.27;
  private static final double LNG_MIN = 126.80;
  private static final double LNG_SPAN = 0.38;

  @Override
  public Point geocode(String roadAddress) {
    long hash = Integer.toUnsignedLong(roadAddress == null ? 0 : roadAddress.hashCode());
    double latitude = LAT_MIN + (hash % 1000) / 1000.0 * LAT_SPAN;
    double longitude = LNG_MIN + (hash / 1000 % 1000) / 1000.0 * LNG_SPAN;
    log.info(
        "지오코딩 mock 변환. roadAddress={}, latitude={}, longitude={}",
        roadAddress,
        latitude,
        longitude);
    return GeometryUtil.toPoint(latitude, longitude);
  }
}
