package com.magampick.point.repository;

import com.magampick.point.domain.PointAccrual;
import com.magampick.point.domain.PointAccrualStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PointAccrualRepository extends JpaRepository<PointAccrual, Long> {

  /**
   * 소비자의 ACTIVE 적립 lot 잔량 합산. 잔액 없으면 0 반환.
   *
   * @param customerId 소비자 ID
   * @return 사용 가능 포인트 합계
   */
  @Query(
      "select coalesce(sum(a.remainingAmount), 0) "
          + "from PointAccrual a "
          + "where a.customer.id = :customerId "
          + "and a.status = com.magampick.point.domain.PointAccrualStatus.ACTIVE")
  long sumActiveRemainingByCustomerId(@Param("customerId") Long customerId);

  /**
   * 소비자의 특정 상태 적립 lot FIFO 순 조회. earnedAt asc, id asc — 오래된 것부터 차감.
   *
   * @param customerId 소비자 ID
   * @param status 조회할 lot 상태
   * @return FIFO 정렬된 적립 lot 목록
   */
  List<PointAccrual> findByCustomerIdAndStatusOrderByEarnedAtAscIdAsc(
      Long customerId, PointAccrualStatus status);
}
