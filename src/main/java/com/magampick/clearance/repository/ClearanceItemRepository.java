package com.magampick.clearance.repository;

import com.magampick.clearance.domain.ClearanceItem;
import com.magampick.clearance.domain.ClearanceItemStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClearanceItemRepository extends JpaRepository<ClearanceItem, Long> {

  /**
   * 소비자 상세 조회용 — store + product 함께 fetch. N+1 방지.
   *
   * @param id 떨이 상품 ID
   * @return store·product 초기화된 ClearanceItem
   */
  @EntityGraph(attributePaths = {"store", "product"})
  @Query("SELECT c FROM ClearanceItem c WHERE c.id = :id")
  Optional<ClearanceItem> findByIdWithStoreAndProduct(@Param("id") Long id);

  boolean existsByProductIdAndStatus(Long productId, ClearanceItemStatus status);

  @EntityGraph(attributePaths = "product")
  Page<ClearanceItem> findByStoreId(Long storeId, Pageable pageable);

  /**
   * 소비자 마감할인 탭 — 활성(OPEN) 떨이 목록. N+1 방지용 product fetch 포함.
   *
   * @param storeId 매장 ID
   * @param status {@link ClearanceItemStatus#OPEN}
   * @return 활성 떨이 목록 (product 초기화 포함)
   */
  @EntityGraph(attributePaths = "product")
  List<ClearanceItem> findByStoreIdAndStatus(Long storeId, ClearanceItemStatus status);

  Optional<ClearanceItem> findByIdAndStoreId(Long id, Long storeId);

  @Modifying
  @Query(
      "UPDATE ClearanceItem c SET c.status = com.magampick.clearance.domain.ClearanceItemStatus.CLOSED"
          + " WHERE c.status = com.magampick.clearance.domain.ClearanceItemStatus.OPEN AND c.pickupEndAt < :now")
  int closeExpiredItems(@Param("now") LocalDateTime now);

  /**
   * 마감 임박 특가 조회 (홈 피드). 기본 주소지 5km 이내, OPEN 매장, 오늘 영업, status=OPEN 떨이 중 pickupEndAt ∈ [now, until].
   * 마감 가까운 순(ASC), LIMIT 5.
   *
   * @param lat origin 위도
   * @param lng origin 경도
   * @param today DayOfWeek.name() 예) "SATURDAY"
   * @param now 현재 시각
   * @param until now + 60분
   * @return {@link ClosingDealCandidate} projection 목록 (최대 5개, 마감 빠른 순)
   */
  @Query(
      value =
          """
          SELECT c.id          AS id,
                 s.name        AS storeName,
                 c.name        AS productName,
                 p.image_url   AS imageUrl,
                 c.regular_price AS regularPrice,
                 c.sale_price  AS salePrice,
                 c.pickup_end_at AS pickupDeadline
          FROM clearance_items c
          JOIN stores s ON s.id = c.store_id
          LEFT JOIN products p ON p.id = c.product_id
          WHERE c.status = 'OPEN'
            AND c.pickup_end_at >= :now
            AND c.pickup_end_at <= :until
            AND s.deleted_at IS NULL
            AND s.operation_status = 'OPEN'
            AND ST_DWithin(s.location, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography, 5000)
            AND EXISTS (
                SELECT 1 FROM store_business_hours h
                WHERE h.store_id = s.id AND h.day_of_week = :today
            )
          ORDER BY c.pickup_end_at ASC
          LIMIT 5
          """,
      nativeQuery = true)
  List<ClosingDealCandidate> findClosingSoonDeals(
      @Param("lat") double lat,
      @Param("lng") double lng,
      @Param("today") String today,
      @Param("now") LocalDateTime now,
      @Param("until") LocalDateTime until);

  /**
   * 매장별 활성 떨이 배치 집계 — N+1 방지. 결과: [storeId, activeDealCount, maxDiscountRate, nearestPickupEndAt].
   * 결과 없는 storeId 는 포함되지 않음 → 호출 측 기본값 처리.
   *
   * @param storeIds 매장 ID 목록
   * @param status 집계 대상 status (OPEN)
   * @return Object[] 배열 목록: [0]=Long storeId, [1]=Long count, [2]=BigDecimal maxDiscountRate,
   *     [3]=LocalDateTime nearestPickupEndAt
   */
  @Query(
      """
      SELECT c.store.id, COUNT(c),
             MAX((c.regularPrice - c.salePrice) / c.regularPrice),
             MIN(c.pickupEndAt)
      FROM ClearanceItem c
      WHERE c.store.id IN :storeIds AND c.status = :status
      GROUP BY c.store.id
      """)
  List<Object[]> findActiveDealSummaryByStoreIds(
      @Param("storeIds") Collection<Long> storeIds, @Param("status") ClearanceItemStatus status);
}
