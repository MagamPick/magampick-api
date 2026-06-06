package com.magampick.store.dto;

/** 소비자 상품 상세 화면 공통 매장 미리보기. 거리(km) + 오늘 영업 종료 시각(HH:mm). */
public record StorePreviewInfo(double distanceKm, String closingTime) {}
