package com.magampick.geocode.service;

import com.magampick.geocode.domain.GeocodeBuilding;
import com.magampick.geocode.exception.GeocodeErrorCode;
import com.magampick.geocode.repository.GeocodeBuildingRepository;
import com.magampick.global.common.GeometryUtil;
import com.magampick.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Point;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * 자체 DB(geocode_buildings, 위치정보요약DB 적재) 기반 지오코딩 (prod). ADR-002 B안.
 *
 * <ul>
 *   <li>정방향: 다음 위젯 결과 → 도로명 자연키({@link RoadAddressParser}) → 정확 매칭. 파싱 실패·매칭 미스 모두 {@code
 *       GEOCODING_FAILED}.
 *   <li>역방향: PostGIS 최근접 건물의 도로명주소 라벨 (매칭 없으면 null).
 * </ul>
 */
@Slf4j
@Service
@Profile("!test")
@RequiredArgsConstructor
public class DbGeocodingService implements GeocodingService {

  private final GeocodeBuildingRepository geocodeBuildingRepository;

  @Override
  public Point geocode(GeocodeQuery query) {
    if (query == null) {
      throw new BusinessException(GeocodeErrorCode.GEOCODING_FAILED);
    }
    GeocodeKey key;
    try {
      key = RoadAddressParser.parse(query.sigunguCode(), query.roadnameCode(), query.roadAddress());
    } catch (IllegalArgumentException e) {
      log.info("정방향 지오코딩 파싱 실패. query={}", query, e);
      throw new BusinessException(GeocodeErrorCode.GEOCODING_FAILED);
    }
    return geocodeBuildingRepository
        .findByRoadNameCodeAndUndergroundAndBuildingMainNoAndBuildingSubNo(
            key.roadNameCode(), key.underground(), key.buildingMainNo(), key.buildingSubNo())
        .map(GeocodeBuilding::getLocation)
        .orElseThrow(
            () -> {
              log.info("정방향 지오코딩 매칭 실패. key={}", key);
              return new BusinessException(GeocodeErrorCode.GEOCODING_FAILED);
            });
  }

  @Override
  public String reverseGeocode(Point point) {
    if (point == null) {
      return null;
    }
    return geocodeBuildingRepository
        .findNearestRoadAddress(GeometryUtil.longitude(point), GeometryUtil.latitude(point))
        .orElse(null);
  }
}
