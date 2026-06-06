package com.magampick.store.service;

import com.magampick.global.common.GeometryUtil;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Point;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * 지오코딩 Mock (test). ADR-002 자체 DB(geocode_buildings) 실연동은 test 외 프로파일의 {@link DbGeocodingService} 가
 * 담당하며, 본 Mock 은 1.5M행 적재 없이 dev/test 가 돌도록 대체한다.
 *
 * <ul>
 *   <li>정방향: 도로명 주소 문자열 해시로 서울 bounding box 내 결정적 좌표 — 시연 시 매장이 한 점에 몰리지 않게 한다.
 *   <li>역방향: 결정적 stub 라벨 (실 최근접 매칭 아님).
 * </ul>
 */
@Slf4j
@Service
@Profile("test")
public class MockGeocodingService implements GeocodingService {

  // 서울 대략 bounding box: 위도 37.43~37.70, 경도 126.80~127.18
  private static final double LAT_MIN = 37.43;
  private static final double LAT_SPAN = 0.27;
  private static final double LNG_MIN = 126.80;
  private static final double LNG_SPAN = 0.38;

  @Override
  public Point geocode(GeocodeQuery query) {
    String roadAddress = query == null ? null : query.roadAddress();
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

  @Override
  public String reverseGeocode(Point point) {
    log.info("역지오코딩 mock. point={}", point);
    return "서울특별시 중구 세종대로 110";
  }
}
