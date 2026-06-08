package com.magampick.address.repository;

import com.magampick.address.domain.Address;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AddressRepository extends JpaRepository<Address, Long> {

  long countByCustomerId(Long customerId);

  List<Address> findByCustomerIdOrderByIsDefaultDescCreatedAtAscIdAsc(Long customerId);

  Optional<Address> findByCustomerIdAndIsDefaultTrue(Long customerId);

  Optional<Address> findFirstByCustomerIdAndIdNotOrderByCreatedAtAscIdAsc(
      Long customerId, Long excludeId);

  /**
   * 매장 위치 기준 N미터 이내의 기본 주소지를 가진 소비자 ID 목록 반환. 떨이 등록·임박 알림 발송 대상 계산용.
   *
   * @param lat 기준 위도
   * @param lng 기준 경도
   * @param distanceMeters 반경 (미터)
   * @return 기준점 반경 이내 기본 주소지를 가진 소비자 ID 목록
   */
  @Query(
      value =
          """
          SELECT a.customer_id
          FROM addresses a
          WHERE a.is_default = true
            AND ST_DWithin(
                  a.location,
                  ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
                  :distanceMeters
                )
          """,
      nativeQuery = true)
  List<Long> findCustomerIdsWithDefaultAddressNear(
      @Param("lat") double lat,
      @Param("lng") double lng,
      @Param("distanceMeters") double distanceMeters);
}
