package com.magampick.store.dto;

/**
 * 소비자 매장 목록 정렬 기준. 없거나 잘못된 값이면 {@link #RECOMMENDED} 로 fallback.
 *
 * <ul>
 *   <li>RECOMMENDED — 추천 (거리·평점·떨이 가중치 합산 desc)
 *   <li>DISTANCE — 거리 asc
 *   <li>DISCOUNT — 최대 할인율 desc, 떨이 없으면 뒤로
 *   <li>CLOSING — 가장 가까운 픽업 마감 asc, 떨이 없으면 뒤로
 *   <li>RATING — 평점 desc, 리뷰 없으면 뒤로
 * </ul>
 */
public enum StoreSort {
  RECOMMENDED,
  DISTANCE,
  DISCOUNT,
  CLOSING,
  RATING;

  /** 쿼리 파라미터 문자열 → enum. null 이거나 잘못된 값이면 {@link #RECOMMENDED} 반환. */
  public static StoreSort fromParam(String param) {
    if (param == null) {
      return RECOMMENDED;
    }
    try {
      return StoreSort.valueOf(param.toUpperCase());
    } catch (IllegalArgumentException e) {
      return RECOMMENDED;
    }
  }
}
