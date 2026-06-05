package com.magampick.geocode.domain;

import java.io.Serializable;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * {@link GeocodeBuilding} 복합 키. 도로명 자연키 = 도로명코드 + 지하여부 + 건물본번 + 건물부번. 위치정보요약DB 검증상 이 4-튜플이 좌표상 유일하다
 * (ADR-002 B안).
 */
@NoArgsConstructor
@EqualsAndHashCode
public class GeocodeBuildingId implements Serializable {

  private String roadNameCode;
  private boolean underground;
  private int buildingMainNo;
  private int buildingSubNo;
}
