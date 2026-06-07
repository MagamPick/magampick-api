package com.magampick.store.dto;

/** 지도 기반 매장 조회 응답 DTO. 카카오맵 마커 1개에 대응하는 매장 정보 + 거리·평점·떨이 집계. */
public record MapStoreResponse(
    Long id,
    String name,
    String imageUrl,
    double latitude,
    double longitude,
    double distanceKm,
    double rating,
    long activeDealCount,
    int maxDiscountRate) {}
