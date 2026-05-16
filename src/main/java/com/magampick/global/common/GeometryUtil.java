package com.magampick.global.common;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

/**
 * 위경도 ↔ PostGIS Point (SRID 4326) 변환 헬퍼. WGS84 기준. PostGIS GEOGRAPHY(POINT, 4326) 컬럼에 매핑되는 JTS
 * Point 를 생성하고 역으로 lat/lng 를 꺼낸다.
 */
public final class GeometryUtil {

  /** WGS84 (EPSG:4326). PostGIS GEOGRAPHY 와 일치. */
  public static final int SRID_WGS84 = 4326;

  private static final GeometryFactory FACTORY = new GeometryFactory();

  private GeometryUtil() {}

  /** 위/경도 → JTS Point. SRID 4326 자동 부착. */
  public static Point toPoint(double latitude, double longitude) {
    // GeometryFactory.createPoint(Coordinate(x, y)) — x=lng, y=lat
    Point point = FACTORY.createPoint(new Coordinate(longitude, latitude));
    point.setSRID(SRID_WGS84);
    return point;
  }

  /** Point → 위도 (null-safe). */
  public static Double latitude(Point point) {
    return point == null ? null : point.getY();
  }

  /** Point → 경도 (null-safe). */
  public static Double longitude(Point point) {
    return point == null ? null : point.getX();
  }
}
