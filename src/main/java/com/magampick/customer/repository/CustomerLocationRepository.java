package com.magampick.customer.repository;

import com.magampick.customer.domain.CustomerLocation;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerLocationRepository extends JpaRepository<CustomerLocation, Long> {

  Optional<CustomerLocation> findByCustomerId(Long customerId);

  /**
   * 매장 위치 기준 N미터 이내 + 신선도(freshThreshold 이후) 소비자 ID 목록 반환. 떨이 등록·마감임박 알림 ② 현재위치 대상 계산용.
   *
   * @param lat 기준 위도
   * @param lng 기준 경도
   * @param distanceMeters 반경 (미터)
   * @param freshThreshold location_updated_at 최솟값 (now - 1시간)
   * @return 반경 이내 + 신선도 통과 소비자 ID 목록
   */
  @Query(
      value =
          """
          SELECT cl.customer_id
          FROM customer_locations cl
          WHERE cl.location_updated_at >= :freshThreshold
            AND ST_DWithin(
                  cl.location,
                  ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
                  :distanceMeters
                )
          """,
      nativeQuery = true)
  List<Long> findCustomerIdsNear(
      @Param("lat") double lat,
      @Param("lng") double lng,
      @Param("distanceMeters") double distanceMeters,
      @Param("freshThreshold") LocalDateTime freshThreshold);
}
