package com.magampick.store.service;

/**
 * 정방향 지오코딩 조회 입력 — 다음 우편번호 위젯 반환값. {@code sigunguCode}(5) + {@code roadnameCode}(7) 로 도로명코드를,
 * {@code roadAddress} 의 마지막 토큰으로 건물본번/부번을 조립한다 ({@link RoadAddressParser}).
 */
public record GeocodeQuery(String sigunguCode, String roadnameCode, String roadAddress) {}
