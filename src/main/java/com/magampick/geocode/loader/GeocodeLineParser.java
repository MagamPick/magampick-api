package com.magampick.geocode.loader;

import java.util.Optional;

/**
 * 위치정보요약DB(CP949, '|' 구분, 18컬럼) 1행 → {@link GeocodeRow}. 좌표 누락(비공개/공개제한 건물)·컬럼 부족·숫자 파싱 실패 행은
 * {@link Optional#empty()} 로 skip. road_address 는 역지오코딩 라벨용으로 합성한다.
 */
public final class GeocodeLineParser {

  private static final int MIN_COLUMNS = 18;

  // 0-기반 컬럼 인덱스 (레이아웃 순번 - 1)
  private static final int SIDO = 3;
  private static final int SIGUNGU = 4;
  private static final int ROAD_NAME_CODE = 6;
  private static final int ROAD_NAME = 7;
  private static final int UNDERGROUND = 8;
  private static final int MAIN_NO = 9;
  private static final int SUB_NO = 10;
  private static final int X = 16;
  private static final int Y = 17;

  private GeocodeLineParser() {}

  public static Optional<GeocodeRow> parse(String line) {
    if (line == null) {
      return Optional.empty();
    }
    String[] c = line.split("\\|", -1);
    if (c.length < MIN_COLUMNS) {
      return Optional.empty();
    }
    Integer main = parseIntOrNull(c[MAIN_NO]);
    Integer sub = parseIntOrNull(c[SUB_NO]);
    Double x = parseDoubleOrNull(c[X]);
    Double y = parseDoubleOrNull(c[Y]);
    // 좌표 누락(공개제한)·본번/부번 결측 행은 적재 제외 (location NOT NULL)
    if (main == null || sub == null || x == null || y == null) {
      return Optional.empty();
    }
    boolean underground = "1".equals(c[UNDERGROUND].trim());
    String roadAddress =
        composeRoadAddress(c[SIDO], c[SIGUNGU], c[ROAD_NAME], underground, main, sub);
    return Optional.of(
        new GeocodeRow(c[ROAD_NAME_CODE], underground, main, sub, roadAddress, x, y));
  }

  /** "{시도명} {시군구명} {도로명} [지하 ]{본번}[-{부번}]" */
  private static String composeRoadAddress(
      String sido, String sigungu, String roadName, boolean underground, int main, int sub) {
    StringBuilder sb = new StringBuilder();
    sb.append(sido).append(' ').append(sigungu).append(' ').append(roadName).append(' ');
    if (underground) {
      sb.append("지하 ");
    }
    sb.append(main);
    if (sub > 0) {
      sb.append('-').append(sub);
    }
    return sb.toString();
  }

  private static Integer parseIntOrNull(String s) {
    try {
      return Integer.valueOf(s.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static Double parseDoubleOrNull(String s) {
    try {
      return Double.valueOf(s.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
