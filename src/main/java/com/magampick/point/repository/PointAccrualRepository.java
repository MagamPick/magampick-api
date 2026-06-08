package com.magampick.point.repository;

import com.magampick.point.domain.PointAccrual;
import com.magampick.point.domain.PointAccrualStatus;
import java.time.LocalDateTime;
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

  /**
   * 주문 ID 로 적립 lot 조회. clawback 시 주문에 연결된 EARN lot 을 찾는다.
   *
   * @param orderId 주문 ID
   * @return 해당 주문의 적립 lot 목록
   */
  List<PointAccrual> findByOrderId(Long orderId);

  /**
   * 만료 기간이 경과한 ACTIVE 적립 lot 조회. 소멸 배치에서 사용.
   *
   * @param status 조회할 상태 (ACTIVE)
   * @param now 기준 시각 — expiresAt 이 now 이전인 lot 만 반환
   * @return 소멸 대상 적립 lot 목록
   */
  List<PointAccrual> findByStatusAndExpiresAtBefore(PointAccrualStatus status, LocalDateTime now);

  /**
   * 소멸 30일 전 알림 대상 ACTIVE lot 조회. expiresAt 이 [from, to] 범위이고 아직 알림 미발송인 lot.
   *
   * @param status 조회할 상태 (ACTIVE)
   * @param from 기준 시작 (inclusive)
   * @param to 기준 종료 (inclusive)
   * @return 알림 대상 lot 목록
   */
  @Query(
      "select a from PointAccrual a join fetch a.customer "
          + "where a.status = :status "
          + "and a.expiresAt between :from and :to "
          + "and a.expiryAlertSentAt is null")
  List<PointAccrual> findExpiringForAlert(
      @Param("status") PointAccrualStatus status,
      @Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to);
}
