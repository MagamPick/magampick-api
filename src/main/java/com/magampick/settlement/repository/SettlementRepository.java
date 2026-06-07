package com.magampick.settlement.repository;

import com.magampick.settlement.domain.Settlement;
import com.magampick.settlement.domain.SettlementStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {

  /** 매장별 정산 목록 — 최신 회차 순(year·month·half desc). */
  List<Settlement> findByStoreIdOrderByYearDescMonthDescHalfDesc(Long storeId);

  /** 매장의 가장 최근 SCHEDULED 회차. */
  Optional<Settlement> findTopByStoreIdAndStatusOrderByYearDescMonthDescHalfDesc(
      Long storeId, SettlementStatus status);

  /** 중복 방지 — 이미 생성된 회차인지 확인. */
  boolean existsByStoreIdAndYearAndMonthAndHalf(Long storeId, int year, int month, int half);

  /**
   * 특정 기간 내 완료된 주문의 매장별 합계 금액 집계 (환불 승인 건 제외).
   *
   * <p>LEFT JOIN refunds + WHERE r.id IS NULL 로 환불 없는 완료 주문만 집계.
   *
   * @return Object[] { store_id (Long/BigInteger), total_amount (BigDecimal) }
   */
  @Query(
      value =
          """
          SELECT o.store_id, SUM(o.total_price) AS total_amount
          FROM orders o
          LEFT JOIN refunds r ON r.order_id = o.id AND r.status = 'APPROVED'
          WHERE o.status = 'COMPLETED'
            AND o.completed_at >= :periodStart
            AND o.completed_at <= :periodEnd
            AND r.id IS NULL
          GROUP BY o.store_id
          """,
      nativeQuery = true)
  List<Object[]> findGrossAmountByPeriodRaw(
      @Param("periodStart") LocalDateTime periodStart, @Param("periodEnd") LocalDateTime periodEnd);
}
