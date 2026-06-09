package com.magampick.coupon.repository;

import com.magampick.coupon.domain.Coupon;
import com.magampick.coupon.domain.CouponKind;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 쿠폰 마스터 Repository. */
public interface CouponRepository extends JpaRepository<Coupon, Long> {

  /** 특정 kind 의 활성 쿠폰 목록 (EVENT 이벤트 목록 조회용). */
  List<Coupon> findByKindAndActiveTrue(CouponKind kind);

  /** 특정 kind 의 전체 쿠폰 목록 (관리자 이벤트 전체 조회용, 생성 최신순). */
  List<Coupon> findByKindOrderByCreatedAtDesc(CouponKind kind);

  /** 특정 kind 의 활성 쿠폰 첫 번째 (SIGNUP 마스터 단건 조회용 — id 오름차순으로 결정적 조회). */
  Optional<Coupon> findFirstByKindAndActiveTrueOrderByIdAsc(CouponKind kind);

  /**
   * 선착순 발급 원자적 카운트 증가. EVENT 쿠폰 한도 내에서만 증가한다.
   *
   * @return 갱신된 행 수 (0 = 한도 초과 또는 조건 불충족, 1 = 성공)
   */
  @Modifying(clearAutomatically = true)
  @Query(
      "update Coupon c set c.issuedCount = c.issuedCount + 1 "
          + "where c.id = :couponId and c.active = true "
          + "and c.kind = com.magampick.coupon.domain.CouponKind.EVENT "
          + "and (c.issueLimit is null or c.issuedCount < c.issueLimit)")
  int incrementIssuedCountIfAvailable(@Param("couponId") Long couponId);
}
