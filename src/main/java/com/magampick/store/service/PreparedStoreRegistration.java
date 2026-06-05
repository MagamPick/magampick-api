package com.magampick.store.service;

import com.magampick.store.dto.StoreCreateRequest;
import org.locationtech.jts.geom.Point;

/** 트랜잭션 시작 전에 완료한 첫 매장 등록 외부 처리 결과. */
public record PreparedStoreRegistration(
    String businessNumber, StoreCreateRequest request, Point location, String imageUrl) {}
