package com.magampick.review.repository;

import com.magampick.review.domain.Review;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, Long> {

  /** 매장 리뷰 목록 (최신순, soft-delete 제외). customer JOIN FETCH → authorNickname N+1 방지. */
  @Query(
      "SELECT r FROM Review r JOIN FETCH r.customer "
          + "WHERE r.store.id = :storeId AND r.deletedAt IS NULL "
          + "ORDER BY r.createdAt DESC")
  Slice<Review> findByStoreIdOrderByCreatedAtDesc(
      @Param("storeId") Long storeId, Pageable pageable);

  /** 매장 평점/건수 집계 (soft-delete 제외). 결과: [avg, count] — 0건이면 avg=null, count=0. */
  @Query(
      "SELECT AVG(CAST(r.rating AS double)), COUNT(r) "
          + "FROM Review r "
          + "WHERE r.store.id = :storeId AND r.deletedAt IS NULL")
  List<Object[]> findStoreRatingStats(@Param("storeId") Long storeId);

  /**
   * 매장 배치 평점 집계 N+1 방지 (soft-delete 제외). 결과 행: [store_id, avg, count]. 결과 없는 매장은 포함 안 됨 → 호출 측에서 0
   * 기본값 처리.
   */
  @Query(
      "SELECT r.store.id, AVG(CAST(r.rating AS double)), COUNT(r) "
          + "FROM Review r "
          + "WHERE r.store.id IN :storeIds AND r.deletedAt IS NULL "
          + "GROUP BY r.store.id")
  List<Object[]> findStoreRatingsStatsBatch(@Param("storeIds") Collection<Long> storeIds);

  /** 별점 분포 (soft-delete 제외). 결과 행: [rating, count]. 없는 별점은 포함 안 됨 → 호출 측에서 0 처리. */
  @Query(
      "SELECT r.rating, COUNT(r) "
          + "FROM Review r "
          + "WHERE r.store.id = :storeId AND r.deletedAt IS NULL "
          + "GROUP BY r.rating")
  List<Object[]> findRatingDistribution(@Param("storeId") Long storeId);

  /** 떨이 상품 평점/건수 집계 (soft-delete 제외). 상품 평점 = 해당 떨이를 주문한 리뷰의 평균. 결과: [avg, count]. */
  @Query(
      "SELECT AVG(CAST(r.rating AS double)), COUNT(r) "
          + "FROM Review r "
          + "JOIN r.order o "
          + "JOIN o.orderItems oi "
          + "WHERE oi.clearanceItem.id = :clearanceItemId AND r.deletedAt IS NULL")
  List<Object[]> findClearanceItemRatingStats(@Param("clearanceItemId") Long clearanceItemId);
}
