package com.magampick.coupon.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.magampick.TestcontainersConfiguration;
import com.magampick.coupon.domain.Coupon;
import com.magampick.coupon.domain.CouponDiscountType;
import com.magampick.coupon.domain.CouponKind;
import com.magampick.coupon.domain.CouponStatus;
import com.magampick.coupon.domain.UserCoupon;
import com.magampick.customer.domain.Customer;
import com.magampick.customer.repository.CustomerRepository;
import com.magampick.global.config.JpaAuditingConfig;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

/**
 * UserCoupon Repository 커스텀 쿼리 검증. findByCustomerIdWithCoupon — 정렬 + fetch 조인
 * existsByCustomerIdAndCouponId — 존재 여부 UNIQUE(customer,coupon) — 중복 발급 차단
 * findClaimedCouponIdsByCustomerId — 수령 ID 집합
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
class UserCouponRepositoryTest {

  @Autowired CouponRepository couponRepository;
  @Autowired UserCouponRepository userCouponRepository;
  @Autowired CustomerRepository customerRepository;

  private Customer customer;
  private Coupon coupon1;
  private Coupon coupon2;

  @BeforeEach
  void setUp() {
    customer =
        customerRepository.save(
            Customer.builder()
                .email("uc_test_" + System.nanoTime() + "@test.com")
                .passwordHash("x")
                .nickname("쿠폰테스터")
                .build());
    coupon1 =
        couponRepository.save(
            Coupon.builder()
                .kind(CouponKind.EVENT)
                .label("이벤트1")
                .discountType(CouponDiscountType.AMOUNT)
                .discountValue(1000)
                .minOrder(0)
                .validUntil(LocalDate.now().plusDays(7))
                .validityDays(null)
                .issueLimit(null)
                .active(true)
                .build());
    coupon2 =
        couponRepository.save(
            Coupon.builder()
                .kind(CouponKind.EVENT)
                .label("이벤트2")
                .discountType(CouponDiscountType.RATE)
                .discountValue(10)
                .minOrder(5000)
                .validUntil(LocalDate.now().plusDays(14))
                .validityDays(null)
                .issueLimit(null)
                .active(true)
                .build());
  }

  private UserCoupon saveUserCoupon(Customer customer, Coupon coupon, LocalDateTime issuedAt) {
    return userCouponRepository.save(
        UserCoupon.builder()
            .customer(customer)
            .coupon(coupon)
            .status(CouponStatus.USABLE)
            .expiresAt(LocalDate.now().plusDays(30))
            .issuedAt(issuedAt)
            .build());
  }

  // ── findByCustomerIdWithCoupon ────────────────────────────────────────────────

  @Test
  void findByCustomerIdWithCoupon_정렬_최근발급순() {
    // given: coupon1 → 어제, coupon2 → 지금
    saveUserCoupon(customer, coupon1, LocalDateTime.now().minusDays(1));
    saveUserCoupon(customer, coupon2, LocalDateTime.now());

    // when
    List<UserCoupon> result = userCouponRepository.findByCustomerIdWithCoupon(customer.getId());

    // then: 최근 발급(coupon2) 먼저
    assertThat(result).hasSize(2);
    assertThat(result.get(0).getCoupon().getId()).isEqualTo(coupon2.getId());
    assertThat(result.get(1).getCoupon().getId()).isEqualTo(coupon1.getId());
  }

  @Test
  void findByCustomerIdWithCoupon_fetch조인_coupon_조회됨() {
    // given
    saveUserCoupon(customer, coupon1, LocalDateTime.now());

    // when
    List<UserCoupon> result = userCouponRepository.findByCustomerIdWithCoupon(customer.getId());

    // then: coupon 페치되어 있음 (LazyInitializationException 없음)
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getCoupon().getLabel()).isNotNull();
  }

  // ── existsByCustomerIdAndCouponId ─────────────────────────────────────────────

  @Test
  void existsByCustomerIdAndCouponId_발급있으면_true() {
    // given
    saveUserCoupon(customer, coupon1, LocalDateTime.now());

    // when / then
    assertThat(
            userCouponRepository.existsByCustomerIdAndCouponId(customer.getId(), coupon1.getId()))
        .isTrue();
  }

  @Test
  void existsByCustomerIdAndCouponId_발급없으면_false() {
    // when / then (coupon2 는 저장 안 함)
    assertThat(
            userCouponRepository.existsByCustomerIdAndCouponId(customer.getId(), coupon2.getId()))
        .isFalse();
  }

  // ── UNIQUE(customer,coupon) 중복 발급 차단 ──────────────────────────────────────

  @Test
  void UNIQUE_customer_coupon_중복발급_차단() {
    // given: 첫 번째 발급
    saveUserCoupon(customer, coupon1, LocalDateTime.now());
    userCouponRepository.flush();

    // when: 동일 customer + coupon 두 번째 발급
    assertThatThrownBy(
            () -> {
              saveUserCoupon(customer, coupon1, LocalDateTime.now().plusMinutes(1));
              userCouponRepository.flush();
            })
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  // ── findClaimedCouponIdsByCustomerId ─────────────────────────────────────────

  @Test
  void findClaimedCouponIdsByCustomerId_수령한_ID_집합_반환() {
    // given: EVENT coupon1 수령 + SIGNUP 쿠폰 수령 (EVENT 스코프에서 제외되어야 함)
    saveUserCoupon(customer, coupon1, LocalDateTime.now());

    Coupon signupCoupon =
        couponRepository.save(
            Coupon.builder()
                .kind(CouponKind.SIGNUP)
                .label("가입축하_테스트")
                .discountType(CouponDiscountType.RATE)
                .discountValue(20)
                .minOrder(5000)
                .validUntil(null)
                .validityDays(30)
                .issueLimit(null)
                .active(true)
                .build());
    saveUserCoupon(customer, signupCoupon, LocalDateTime.now().minusHours(1));

    // when
    Set<Long> claimed = userCouponRepository.findClaimedCouponIdsByCustomerId(customer.getId());

    // then: EVENT coupon1 만 반환, SIGNUP 쿠폰은 제외됨
    assertThat(claimed).containsExactly(coupon1.getId());
    assertThat(claimed).doesNotContain(coupon2.getId());
    assertThat(claimed).doesNotContain(signupCoupon.getId());
  }

  @Test
  void findClaimedCouponIdsByCustomerId_아무것도_없으면_빈_집합() {
    // when
    Set<Long> claimed = userCouponRepository.findClaimedCouponIdsByCustomerId(customer.getId());

    // then
    assertThat(claimed).isEmpty();
  }

  // ── expireUsableBefore ────────────────────────────────────────────────────────

  @Test
  void expireUsableBefore_USABLE만료만_EXPIRED_USED는유지() {
    // given
    LocalDate today = LocalDate.now();

    // 만료된 USABLE (expiresAt = 어제)
    UserCoupon expiredUsable =
        userCouponRepository.save(
            UserCoupon.builder()
                .customer(customer)
                .coupon(coupon1)
                .status(CouponStatus.USABLE)
                .expiresAt(today.minusDays(1)) // 어제 = 만료
                .issuedAt(LocalDateTime.now().minusDays(30))
                .build());

    // 유효한 USABLE (expiresAt = 미래)
    UserCoupon validUsable =
        userCouponRepository.save(
            UserCoupon.builder()
                .customer(customer)
                .coupon(coupon2)
                .status(CouponStatus.USABLE)
                .expiresAt(today.plusDays(7)) // 미래 = 유효
                .issuedAt(LocalDateTime.now().minusDays(1))
                .build());

    userCouponRepository.flush();

    // when: today 기준으로 만료 처리
    int updated = userCouponRepository.expireUsableBefore(today);

    // then: 만료된 USABLE 1건만 EXPIRED 전이
    assertThat(updated).isEqualTo(1);

    UserCoupon refreshedExpired =
        userCouponRepository.findById(expiredUsable.getId()).orElseThrow();
    assertThat(refreshedExpired.getStatus()).isEqualTo(CouponStatus.EXPIRED);

    UserCoupon refreshedValid = userCouponRepository.findById(validUsable.getId()).orElseThrow();
    assertThat(refreshedValid.getStatus()).isEqualTo(CouponStatus.USABLE); // 유지
  }
}
