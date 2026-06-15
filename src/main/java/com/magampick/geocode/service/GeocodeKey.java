package com.magampick.geocode.service;

/** 도로명 자연키 (정방향 지오코딩 조회용). {@link RoadAddressParser} 가 다음 위젯 결과로부터 조립한다. */
public record GeocodeKey(
    String roadNameCode, boolean underground, int buildingMainNo, int buildingSubNo) {}
