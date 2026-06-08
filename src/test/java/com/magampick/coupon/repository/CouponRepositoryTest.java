package com.magampick.coupon.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.magampick.TestcontainersConfiguration;
import com.magampick.coupon.domain.Coupon;
import com.magampick.coupon.domain.CouponDiscountType;
import com.magampick.coupon.domain.CouponKind;
import com.magampick.global.config.JpaAuditingConfig;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * 쿠폰 Repository 커스텀 쿼리 검증. incrementIssuedCountIfAvailable — 선착순 원자 카운트 증가
 * findFirstByKindAndActiveTrue — SIGNUP 마스터 단건 findByKindAndActiveTrue — EVENT 목록
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
class CouponRepositoryTest {

  @Autowired CouponRepository couponRepository;

  private Coupon saveEventCoupon(int issueLimit, int issuedCount) {
    Coupon c =
        Coupon.builder()
            .kind(CouponKind.EVENT)
            .label("이벤트" + System.nanoTime())
            .discountType(CouponDiscountType.AMOUNT)
            .discountValue(1000)
            .minOrder(0)
            .validUntil(LocalDate.now().plusDays(7))
            .validityDays(null)
            .issueLimit(issueLimit > 0 ? issueLimit : null)
            .active(true)
            .build();
    Coupon saved = couponRepository.save(c);
    // issued_count 직접 설정 (테스트용 — 실제 서비스는 increment 사용)
    if (issuedCount > 0) {
      couponRepository.incrementIssuedCountIfAvailable(saved.getId());
    }
    return couponRepository.findById(saved.getId()).orElseThrow();
  }

  private Coupon saveUnlimitedEventCoupon() {
    Coupon c =
        Coupon.builder()
            .kind(CouponKind.EVENT)
            .label("무제한이벤트" + System.nanoTime())
            .discountType(CouponDiscountType.RATE)
            .discountValue(10)
            .minOrder(0)
            .validUntil(LocalDate.now().plusDays(7))
            .validityDays(null)
            .issueLimit(null)
            .active(true)
            .build();
    return couponRepository.save(c);
  }

  private Coupon saveSignupCoupon() {
    Coupon c =
        Coupon.builder()
            .kind(CouponKind.SIGNUP)
            .label("가입축하" + System.nanoTime())
            .discountType(CouponDiscountType.RATE)
            .discountValue(20)
            .minOrder(5000)
            .validUntil(null)
            .validityDays(30)
            .issueLimit(null)
            .active(true)
            .build();
    return couponRepository.save(c);
  }

  // ── incrementIssuedCountIfAvailable ──────────────────────────────────────────

  @Test
  void incrementIssuedCountIfAvailable_한도내_1행_반환() {
    // given: issue_limit=5, issued_count=0
    Coupon coupon = saveEventCoupon(5, 0);

    // when
    int updated = couponRepository.incrementIssuedCountIfAvailable(coupon.getId());

    // then
    assertThat(updated).isEqualTo(1);
    Coupon refreshed = couponRepository.findById(coupon.getId()).orElseThrow();
    assertThat(refreshed.getIssuedCount()).isEqualTo(1);
  }

  @Test
  void incrementIssuedCountIfAvailable_한도도달_0행_반환() {
    // given: issue_limit=1, issued_count=1 (한도 소진)
    Coupon coupon = saveEventCoupon(1, 0);
    // 먼저 한 번 증가시켜 한도를 채움
    couponRepository.incrementIssuedCountIfAvailable(coupon.getId());
    couponRepository.flush();

    // when: 두 번째 시도
    int updated = couponRepository.incrementIssuedCountIfAvailable(coupon.getId());

    // then: 한도 초과 → 0
    assertThat(updated).isEqualTo(0);
  }

  @Test
  void incrementIssuedCountIfAvailable_무제한_항상_1행() {
    // given: issue_limit=null (무제한)
    Coupon coupon = saveUnlimitedEventCoupon();

    // when: 여러 번 증가
    int first = couponRepository.incrementIssuedCountIfAvailable(coupon.getId());
    int second = couponRepository.incrementIssuedCountIfAvailable(coupon.getId());

    // then: 항상 1
    assertThat(first).isEqualTo(1);
    assertThat(second).isEqualTo(1);
  }

  // ── findFirstByKindAndActiveTrue ──────────────────────────────────────────────

  @Test
  void findFirstByKindAndActiveTrue_SIGNUP_반환() {
    // given
    Coupon signup = saveSignupCoupon();

    // when
    Optional<Coupon> result =
        couponRepository.findFirstByKindAndActiveTrueOrderByIdAsc(CouponKind.SIGNUP);

    // then
    assertThat(result).isPresent();
    assertThat(result.get().getKind()).isEqualTo(CouponKind.SIGNUP);
  }

  // ── findByKindAndActiveTrue ────────────────────────────────────────────────────

  @Test
  void findByKindAndActiveTrue_EVENT만_반환() {
    // given: SIGNUP 1개 + EVENT 2개
    saveSignupCoupon();
    saveUnlimitedEventCoupon();
    saveUnlimitedEventCoupon();
    int countBefore = couponRepository.findByKindAndActiveTrue(CouponKind.EVENT).size();

    // then
    assertThat(countBefore).isGreaterThanOrEqualTo(2);
    // SIGNUP 이 섞이지 않았는지 확인
    couponRepository
        .findByKindAndActiveTrue(CouponKind.EVENT)
        .forEach(c -> assertThat(c.getKind()).isEqualTo(CouponKind.EVENT));
  }
}
