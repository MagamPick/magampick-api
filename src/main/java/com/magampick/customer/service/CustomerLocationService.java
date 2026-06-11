package com.magampick.customer.service;

import com.magampick.customer.domain.CustomerLocation;
import com.magampick.customer.dto.CustomerLocationResponse;
import com.magampick.customer.repository.CustomerLocationRepository;
import com.magampick.global.common.GeometryUtil;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 소비자 현재 위치 갱신 서비스. upsert 방식 — 없으면 INSERT, 있으면 UPDATE. */
@Service
@RequiredArgsConstructor
public class CustomerLocationService {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private final CustomerLocationRepository customerLocationRepository;

  /**
   * 소비자 현재 위치를 갱신하고 갱신 결과를 에코 반환한다.
   *
   * @param customerId 인증된 소비자 ID
   * @param lat 위도
   * @param lng 경도
   * @return 저장된 위경도 + 갱신 시각
   */
  @Transactional
  public CustomerLocationResponse updateLocation(Long customerId, double lat, double lng) {
    Point point = GeometryUtil.toPoint(lat, lng);
    LocalDateTime now = LocalDateTime.now(KST);

    customerLocationRepository
        .findByCustomerId(customerId)
        .ifPresentOrElse(
            entity -> entity.update(point, now),
            () -> customerLocationRepository.save(CustomerLocation.of(customerId, point, now)));

    return new CustomerLocationResponse(lat, lng, now);
  }
}
