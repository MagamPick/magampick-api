package com.magampick.geocode.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.magampick.TestcontainersConfiguration;
import com.magampick.geocode.domain.GeocodeBuilding;
import com.magampick.global.common.GeometryUtil;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class GeocodeBuildingRepositoryTest {

  @Autowired GeocodeBuildingRepository repository;
  @Autowired DataSource dataSource;

  @BeforeEach
  void setUp() {
    repository.deleteAll();
    // 종로구 자하문로 94 (청운동 인근)
    repository.save(building("111103100012", false, 94, 0, "서울특별시 종로구 자하문로 94", 37.585, 126.968));
    // 강남구 테헤란로 427 (멀리)
    repository.save(
        building("114003100099", false, 427, 0, "서울특별시 강남구 테헤란로 427", 37.5066, 127.0535));
    repository.flush();
  }

  @Test
  void 자연키로_정확_매칭() {
    Optional<GeocodeBuilding> found =
        repository.findByRoadNameCodeAndUndergroundAndBuildingMainNoAndBuildingSubNo(
            "111103100012", false, 94, 0);

    assertThat(found).isPresent();
    assertThat(found.get().getRoadAddress()).isEqualTo("서울특별시 종로구 자하문로 94");
  }

  @Test
  void 자연키_미스는_빈_결과() {
    Optional<GeocodeBuilding> found =
        repository.findByRoadNameCodeAndUndergroundAndBuildingMainNoAndBuildingSubNo(
            "111103100012", false, 999, 0);

    assertThat(found).isEmpty();
  }

  @Test
  void 최근접_도로명주소_라벨() {
    // 자하문로 94 바로 옆 좌표 → 청운동이 강남보다 가깝다
    Optional<String> nearest = repository.findNearestRoadAddress(126.9682, 37.5851);

    assertThat(nearest).contains("서울특별시 종로구 자하문로 94");
  }

  @Test
  void 적재_SQL_은_5179_좌표를_4326_으로_변환한다() {
    // 위치정보요약DB 청운동 자하문로 94 원본 좌표(EPSG:5179) — GeocodeDataLoader 와 동일한 변환 SQL
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    jdbc.update(
        "INSERT INTO geocode_buildings"
            + " (road_name_code, underground, building_main_no, building_sub_no, road_address,"
            + " location)"
            + " VALUES (?, ?, ?, ?, ?, ST_Transform(ST_SetSRID(ST_MakePoint(?, ?), 5179), 4326))",
        "999999999999",
        false,
        1,
        0,
        "변환 검증용",
        953241.683263,
        1954023.466812);

    Double lat =
        jdbc.queryForObject(
            "SELECT ST_Y(location::geometry) FROM geocode_buildings WHERE road_name_code = ?",
            Double.class,
            "999999999999");
    Double lng =
        jdbc.queryForObject(
            "SELECT ST_X(location::geometry) FROM geocode_buildings WHERE road_name_code = ?",
            Double.class,
            "999999999999");

    // 종로구 청운동 ≈ (37.585, 126.968). 5174 로 오변환 시 수 km 이상 어긋난다.
    assertThat(lat).isCloseTo(37.585, within(0.02));
    assertThat(lng).isCloseTo(126.968, within(0.02));
  }

  private GeocodeBuilding building(
      String code, boolean ug, int main, int sub, String addr, double lat, double lng) {
    return GeocodeBuilding.builder()
        .roadNameCode(code)
        .underground(ug)
        .buildingMainNo(main)
        .buildingSubNo(sub)
        .roadAddress(addr)
        .location(GeometryUtil.toPoint(lat, lng))
        .build();
  }
}
