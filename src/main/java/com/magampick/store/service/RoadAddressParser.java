package com.magampick.store.service;

/**
 * 다음 우편번호 위젯 결과 → 도로명 자연키({@link GeocodeKey}) 조립 (정방향 지오코딩 조회 키). ADR-002 B안.
 *
 * <ul>
 *   <li>도로명코드 = 시군구코드(5) + 도로명번호(7, 좌측 0 패딩).
 *   <li>지하여부 = 건물번호 앞 토큰이 "지하" 면 true.
 *   <li>건물본번/부번 = roadAddress 의 마지막 토큰("본번" 또는 "본번-부번"). 건물명은 roadAddress 에 포함되지 않으므로 마지막 토큰은 항상
 *       건물번호이며, 도로명 안의 숫자(예: 국회대로62길)와 혼동하지 않는다.
 * </ul>
 *
 * 형식 위반(마지막 토큰이 건물번호가 아님 등)은 {@link IllegalArgumentException} 으로, 호출 서비스가 {@code
 * ADDRESS_GEOCODING_FAILED} 로 변환한다.
 */
public final class RoadAddressParser {

  private static final int ROAD_NUMBER_LENGTH = 7;
  private static final String UNDERGROUND_TOKEN = "지하";

  private RoadAddressParser() {}

  public static GeocodeKey parse(String sigunguCode, String roadnameCode, String roadAddress) {
    if (isBlank(sigunguCode) || isBlank(roadnameCode)) {
      throw new IllegalArgumentException("시군구코드/도로명번호가 비어 있습니다");
    }
    if (isBlank(roadAddress)) {
      throw new IllegalArgumentException("도로명주소가 비어 있습니다");
    }
    String roadNameCode = sigunguCode + leftPadZero(roadnameCode, ROAD_NUMBER_LENGTH);

    String[] tokens = roadAddress.trim().split("\\s+");
    String last = tokens[tokens.length - 1];

    boolean underground = false;
    if (last.startsWith(UNDERGROUND_TOKEN)) {
      // "지하2" 병기 형태 (드묾) — 접두 제거 후 번호만 남김
      underground = true;
      last = last.substring(UNDERGROUND_TOKEN.length());
    } else if (tokens.length >= 2 && UNDERGROUND_TOKEN.equals(tokens[tokens.length - 2])) {
      // "... 지하 2" 표준 형태
      underground = true;
    }

    int[] number = parseBuildingNumber(last);
    return new GeocodeKey(roadNameCode, underground, number[0], number[1]);
  }

  /** "94" 또는 "94-1" → [본번, 부번]. 형식 위반 시 예외. */
  private static int[] parseBuildingNumber(String token) {
    if (!token.matches("\\d+(-\\d+)?")) {
      throw new IllegalArgumentException("건물번호 토큰이 아닙니다: " + token);
    }
    int dash = token.indexOf('-');
    if (dash < 0) {
      return new int[] {Integer.parseInt(token), 0};
    }
    return new int[] {
      Integer.parseInt(token.substring(0, dash)), Integer.parseInt(token.substring(dash + 1))
    };
  }

  private static String leftPadZero(String value, int length) {
    if (value.length() >= length) {
      return value;
    }
    return "0".repeat(length - value.length()) + value;
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }
}
