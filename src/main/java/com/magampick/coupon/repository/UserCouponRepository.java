package com.magampick.coupon.repository;

import com.magampick.coupon.domain.CouponStatus;
import com.magampick.coupon.domain.UserCoupon;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 발급된 쿠폰 인스턴스 Repository. */
public interface UserCouponRepository extends JpaRepository<UserCoupon, Long> {

  /** 소비자의 쿠폰함 전체 조회. coupon 페치 조인으로 N+1 방지. */
  @Query(
      "select uc from UserCoupon uc join fetch uc.coupon "
          + "where uc.customer.id = :customerId order by uc.issuedAt desc")
  List<UserCoupon> findByCustomerIdWithCoupon(@Param("customerId") Long customerId);

  /** 소비자가 특정 쿠폰을 이미 받았는지 확인 (1인 1회 중복 방지). */
  boolean existsByCustomerIdAndCouponId(Long customerId, Long couponId);

  /** 소비자가 이미 받은 EVENT 쿠폰 ID 집합 (이벤트 목록 claimed 플래그용). */
  @Query(
      "select uc.coupon.id from UserCoupon uc "
          + "where uc.customer.id = :customerId "
          + "and uc.coupon.kind = com.magampick.coupon.domain.CouponKind.EVENT")
  Set<Long> findClaimedCouponIdsByCustomerId(@Param("customerId") Long customerId);

  /**
   * ID 로 UserCoupon + coupon 페치 조인 조회. 쿠폰 사용/검증 시 N+1 방지.
   *
   * @param id UserCoupon ID
   * @return UserCoupon (coupon 페치 포함)
   */
  @Query("select uc from UserCoupon uc join fetch uc.coupon where uc.id = :id")
  Optional<UserCoupon> findByIdWithCoupon(@Param("id") Long id);

  /**
   * USABLE → USED 원자적 전이. status = USABLE 인 경우에만 업데이트 (동시 사용 경쟁 조건 방지).
   *
   * @param id UserCoupon ID
   * @param now 사용 시각
   * @return 업데이트된 행 수 (0 = 이미 USED 또는 존재하지 않음)
   */
  @Modifying(clearAutomatically = true)
  @Query(
      "update UserCoupon uc set uc.status = com.magampick.coupon.domain.CouponStatus.USED,"
          + " uc.usedAt = :now"
          + " where uc.id = :id and uc.status = com.magampick.coupon.domain.CouponStatus.USABLE")
  int markUsed(@Param("id") Long id, @Param("now") LocalDateTime now);

  /**
   * 만료일이 경과한 USABLE 쿠폰을 EXPIRED 로 일괄 전이. 소멸 배치에서 사용.
   *
   * @param today 기준 날짜 — expiresAt 이 today 이전인 USABLE 쿠폰만 처리
   * @return 업데이트된 행 수
   */
  @Modifying(clearAutomatically = true)
  @Query(
      "update UserCoupon uc set uc.status = com.magampick.coupon.domain.CouponStatus.EXPIRED "
          + "where uc.status = com.magampick.coupon.domain.CouponStatus.USABLE "
          + "and uc.expiresAt < :today")
  int expireUsableBefore(@Param("today") LocalDate today);

  /**
   * 만료 7일 전 알림 대상 USABLE 쿠폰 조회. expiresAt 이 [from, to] 범위이고 아직 알림 미발송인 쿠폰.
   *
   * @param status 조회할 상태 (USABLE)
   * @param from 기준 시작 (inclusive)
   * @param to 기준 종료 (inclusive)
   * @return 알림 대상 UserCoupon 목록
   */
  @Query(
      "select uc from UserCoupon uc join fetch uc.coupon join fetch uc.customer "
          + "where uc.status = :status "
          + "and uc.expiresAt between :from and :to "
          + "and uc.expiryAlertSentAt is null")
  List<UserCoupon> findExpiringForAlert(
      @Param("status") CouponStatus status,
      @Param("from") LocalDate from,
      @Param("to") LocalDate to);
}
