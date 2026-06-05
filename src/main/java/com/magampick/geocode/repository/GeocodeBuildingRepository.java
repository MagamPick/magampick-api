package com.magampick.geocode.repository;

import com.magampick.geocode.domain.GeocodeBuilding;
import com.magampick.geocode.domain.GeocodeBuildingId;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GeocodeBuildingRepository
    extends JpaRepository<GeocodeBuilding, GeocodeBuildingId> {

  /** 정방향: 도로명 자연키 정확 매칭. */
  Optional<GeocodeBuilding> findByRoadNameCodeAndUndergroundAndBuildingMainNoAndBuildingSubNo(
      String roadNameCode, boolean underground, int buildingMainNo, int buildingSubNo);

  /**
   * 역방향: 좌표 최근접 건물의 도로명주소 라벨. GIST KNN({@code <->}) 사용 — geography 거리(m) 기준. JTS Point 직접 바인딩 대신
   * lng/lat 로 받아 SQL 에서 포인트를 만든다 (네이티브 바인딩 안정성).
   */
  @Query(
      value =
          "SELECT road_address FROM geocode_buildings"
              + " ORDER BY location <-> ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography"
              + " LIMIT 1",
      nativeQuery = true)
  Optional<String> findNearestRoadAddress(@Param("lng") double lng, @Param("lat") double lat);
}
