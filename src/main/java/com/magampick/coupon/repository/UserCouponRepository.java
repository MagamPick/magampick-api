package com.magampick.coupon.repository;

import com.magampick.coupon.domain.UserCoupon;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
