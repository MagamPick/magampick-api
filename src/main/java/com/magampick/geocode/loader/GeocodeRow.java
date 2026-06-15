package com.magampick.geocode.loader;

/** 위치정보요약DB 1행을 적재용으로 매핑한 결과. 좌표(x,y)는 EPSG:5179 원본 — 적재 SQL 에서 4326 으로 변환한다. */
public record GeocodeRow(
    String roadNameCode,
    boolean underground,
    int buildingMainNo,
    int buildingSubNo,
    String roadAddress,
    double x,
    double y) {}
