package com.magampick.geocode.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.Point;

/**
 * 지오코딩 참조 건물 (위치정보요약DB 1회 적재, 읽기 전용). 정방향 = 도로명 자연키로 {@link #location} 조회, 역방향 = {@link #location}
 * 최근접으로 {@link #roadAddress} 라벨 조회. 참조 데이터라 created/updated 감사 컬럼({@code BaseEntity})을 두지 않는다.
 */
@Entity
@Table(name = "geocode_buildings")
@IdClass(GeocodeBuildingId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GeocodeBuilding {

  /** 도로명코드 = 시군구코드(5) + 도로명번호(7). */
  @Id
  @Column(name = "road_name_code", nullable = false, length = 12)
  private String roadNameCode;

  @Id
  @Column(name = "underground", nullable = false)
  private boolean underground;

  @Id
  @Column(name = "building_main_no", nullable = false)
  private int buildingMainNo;

  @Id
  @Column(name = "building_sub_no", nullable = false)
  private int buildingSubNo;

  /** 역지오코딩 라벨용 합성 도로명주소. */
  @Column(name = "road_address", nullable = false, length = 200)
  private String roadAddress;

  @Column(name = "location", nullable = false, columnDefinition = "GEOGRAPHY(POINT, 4326)")
  private Point location;

  @Builder
  private GeocodeBuilding(
      String roadNameCode,
      boolean underground,
      int buildingMainNo,
      int buildingSubNo,
      String roadAddress,
      Point location) {
    this.roadNameCode = roadNameCode;
    this.underground = underground;
    this.buildingMainNo = buildingMainNo;
    this.buildingSubNo = buildingSubNo;
    this.roadAddress = roadAddress;
    this.location = location;
  }
}
