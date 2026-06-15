package com.magampick.customer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.Point;

/**
 * 소비자 현재 위치. customers 1:1 종속 (natural key). BaseEntity 미사용 — created_at/updated_at 불필요.
 * location_updated_at 이 마지막 갱신 시각 겸 신선도 판단 기준.
 */
@Entity
@Table(name = "customer_locations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerLocation {

  /** customer_id = PK (natural key). 자동 생성 아님. */
  @Id
  @Column(name = "customer_id")
  private Long customerId;

  /** WGS84 위경도 좌표. Address.location 과 동일 GEOGRAPHY(POINT, 4326) 방식. */
  @Column(name = "location", nullable = false, columnDefinition = "GEOGRAPHY(POINT, 4326)")
  private Point location;

  /** 마지막 위치 갱신 시각 (KST). 1시간 이내이면 신선도 통과. */
  @Column(name = "location_updated_at", nullable = false)
  private LocalDateTime locationUpdatedAt;

  /** 신규 위치 레코드 생성. */
  public static CustomerLocation of(Long customerId, Point location, LocalDateTime now) {
    CustomerLocation cl = new CustomerLocation();
    cl.customerId = customerId;
    cl.location = location;
    cl.locationUpdatedAt = now;
    return cl;
  }

  /** 좌표 + 타임스탬프 덮어쓰기. */
  public void update(Point location, LocalDateTime now) {
    this.location = location;
    this.locationUpdatedAt = now;
  }
}
