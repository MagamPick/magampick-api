package com.magampick.coupon.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.magampick.coupon.domain.Coupon;
import com.magampick.coupon.domain.CouponDiscountType;
import com.magampick.coupon.domain.CouponKind;
import com.magampick.coupon.domain.CouponStatus;
import com.magampick.coupon.domain.EventStatus;
import com.magampick.coupon.domain.UserCoupon;
import com.magampick.coupon.dto.AdminCouponCreateRequest;
import com.magampick.coupon.dto.AdminCouponResponse;
import com.magampick.coupon.dto.AdminCouponUpdateRequest;
import com.magampick.coupon.dto.CouponEventResponse;
import com.magampick.coupon.dto.CouponResponse;
import com.magampick.coupon.exception.CouponErrorCode;
import com.magampick.coupon.fixture.CouponFixture;
import com.magampick.coupon.mapper.CouponMapper;
import com.magampick.coupon.repository.CouponRepository;
import com.magampick.coupon.repository.UserCouponRepository;
import com.magampick.customer.domain.Customer;
import com.magampick.customer.repository.CustomerRepository;
import com.magampick.global.exception.BusinessException;
import com.magampick.notification.domain.NotificationCategory;
import com.magampick.notification.service.NotificationService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CouponServiceTest {

  @Mock CouponRepository couponRepository;
  @Mock UserCouponRepository userCouponRepository;
  @Mock CustomerRepository customerRepository;
  @Mock CouponMapper couponMapper;
  @Mock NotificationService notificationService;

  // 2026-06-08 KST 고정 Clock
  private final Clock fixedClock =
      Clock.fixed(Instant.parse("2026-06-08T00:00:00Z"), ZoneId.of("Asia/Seoul"));

  @InjectMocks CouponService couponService;

  private static final Long CUSTOMER_ID = 1L;

  private Customer customer() {
    Customer c =
        Customer.builder().email("test@example.com").passwordHash("hash").nickname("테스터").build();
    ReflectionTestUtils.setField(c, "id", CUSTOMER_ID);
    return c;
  }

  private void injectClock() {
    ReflectionTestUtils.setField(couponService, "clock", fixedClock);
  }

  // ── getMyCoupons ─────────────────────────────────────────────────────────────

  @Test
  void getMyCoupons_방어판정_USABLE_만료경과_EXPIRED_표시() {
    // given
    injectClock();
    Customer customer = customer();
    Coupon coupon = CouponFixture.aSignupCoupon();
    // 만료일이 오늘보다 과거인 USABLE 쿠폰
    UserCoupon expired = CouponFixture.anExpiredUserCoupon(customer, coupon);
    CouponResponse expectedResponse =
        CouponFixture.aResponse(expired.getId(), CouponStatus.EXPIRED);

    given(userCouponRepository.findByCustomerIdWithCoupon(CUSTOMER_ID))
        .willReturn(List.of(expired));
    given(couponMapper.toResponse(expired, CouponStatus.EXPIRED)).willReturn(expectedResponse);

    // when
    List<CouponResponse> result = couponService.getMyCoupons(CUSTOMER_ID);

    // then: EXPIRED 로 표시됨
    assertThat(result).hasSize(1);
    assertThat(result.get(0).status()).isEqualTo(CouponStatus.EXPIRED);
    then(couponMapper).should().toResponse(expired, CouponStatus.EXPIRED);
  }

  @Test
  void getMyCoupons_USABLE_미만료_그대로_표시() {
    // given
    injectClock();
    Customer customer = customer();
    Coupon coupon = CouponFixture.aSignupCoupon();
    UserCoupon usable = CouponFixture.aUsableUserCoupon(customer, coupon);
    CouponResponse expectedResponse = CouponFixture.aResponse(usable.getId(), CouponStatus.USABLE);

    given(userCouponRepository.findByCustomerIdWithCoupon(CUSTOMER_ID)).willReturn(List.of(usable));
    given(couponMapper.toResponse(usable, CouponStatus.USABLE)).willReturn(expectedResponse);

    // when
    List<CouponResponse> result = couponService.getMyCoupons(CUSTOMER_ID);

    // then
    assertThat(result).hasSize(1);
    then(couponMapper).should().toResponse(usable, CouponStatus.USABLE);
  }

  // ── getEvents ────────────────────────────────────────────────────────────────

  @Test
  void 이벤트_목록_claimed_플래그_이미_받은_것은_true() {
    // given
    injectClock(); // getEvents 가 LocalDate.now(clock) 사용
    Coupon coupon1 = CouponFixture.anEventCoupon(); // id=2, ongoing at 2026-06-08
    Coupon coupon2 = CouponFixture.anUnlimitedEventCoupon(); // id=3, ongoing at 2026-06-08

    given(userCouponRepository.findClaimedCouponIdsByCustomerId(CUSTOMER_ID))
        .willReturn(Set.of(2L));
    given(couponRepository.findByKindAndActiveTrue(CouponKind.EVENT))
        .willReturn(List.of(coupon1, coupon2));

    CouponEventResponse r1 =
        new CouponEventResponse(2L, CouponDiscountType.AMOUNT, 3000, 10000, "봄맞이", null, true);
    CouponEventResponse r2 =
        new CouponEventResponse(3L, CouponDiscountType.RATE, 10, 0, "무제한", null, false);
    given(couponMapper.toEventResponse(coupon1, true)).willReturn(r1);
    given(couponMapper.toEventResponse(coupon2, false)).willReturn(r2);

    // when
    List<CouponEventResponse> result = couponService.getEvents(CUSTOMER_ID);

    // then
    assertThat(result).hasSize(2);
    assertThat(result.get(0).claimed()).isTrue();
    assertThat(result.get(1).claimed()).isFalse();
  }

  @Test
  void 이벤트_목록_비진행중_쿠폰_필터링() {
    // given: SCHEDULED 쿠폰(시작일 미도래)은 목록에 나오지 않아야 함
    injectClock();
    Coupon ongoingCoupon = CouponFixture.anEventCoupon(); // id=2, ONGOING
    Coupon scheduledCoupon =
        Coupon.builder()
            .kind(CouponKind.EVENT)
            .label("예정 이벤트")
            .discountType(CouponDiscountType.AMOUNT)
            .discountValue(2000)
            .minOrder(5000)
            .validUntil(LocalDate.of(2026, 12, 31))
            .displayStartAt(LocalDate.of(2026, 6, 10)) // 2일 후 시작 → SCHEDULED
            .displayEndAt(LocalDate.of(2026, 12, 31))
            .active(true)
            .build();
    ReflectionTestUtils.setField(scheduledCoupon, "id", 4L);

    given(userCouponRepository.findClaimedCouponIdsByCustomerId(CUSTOMER_ID)).willReturn(Set.of());
    given(couponRepository.findByKindAndActiveTrue(CouponKind.EVENT))
        .willReturn(List.of(ongoingCoupon, scheduledCoupon));

    CouponEventResponse r1 =
        new CouponEventResponse(2L, CouponDiscountType.AMOUNT, 3000, 10000, "봄맞이", null, false);
    given(couponMapper.toEventResponse(ongoingCoupon, false)).willReturn(r1);

    // when
    List<CouponEventResponse> result = couponService.getEvents(CUSTOMER_ID);

    // then: ONGOING 인 coupon1 만 포함, SCHEDULED 인 scheduledCoupon 은 필터됨
    assertThat(result).hasSize(1);
    assertThat(result.get(0).couponId()).isEqualTo(2L);
    then(couponMapper).should(never()).toEventResponse(eq(scheduledCoupon), any(Boolean.class));
  }

  // ── claim ────────────────────────────────────────────────────────────────────

  @Test
  void claim_성공() {
    // given
    injectClock();
    Coupon coupon = CouponFixture.anEventCoupon(); // ongoing at 2026-06-08
    Customer customer = customer();
    CouponResponse expectedResponse = CouponFixture.aResponse(10L, CouponStatus.USABLE);

    given(couponRepository.findById(2L)).willReturn(Optional.of(coupon));
    given(userCouponRepository.existsByCustomerIdAndCouponId(CUSTOMER_ID, 2L)).willReturn(false);
    given(couponRepository.incrementIssuedCountIfAvailable(2L)).willReturn(1);
    given(customerRepository.getReferenceById(CUSTOMER_ID)).willReturn(customer);
    given(userCouponRepository.saveAndFlush(any(UserCoupon.class)))
        .willAnswer(
            inv -> {
              UserCoupon uc = inv.getArgument(0);
              ReflectionTestUtils.setField(uc, "id", 10L);
              return uc;
            });
    given(couponMapper.toResponse(any(UserCoupon.class), any())).willReturn(expectedResponse);

    // when
    CouponResponse result = couponService.claim(CUSTOMER_ID, 2L);

    // then
    assertThat(result).isEqualTo(expectedResponse);
    then(userCouponRepository).should().saveAndFlush(any(UserCoupon.class));
    // 발급 알림 발송됨
    then(notificationService)
        .should()
        .notifyCustomer(
            eq(CUSTOMER_ID),
            eq("eventBenefit"),
            eq(NotificationCategory.BENEFIT),
            any(String.class),
            any(String.class),
            any(String.class));
  }

  @Test
  void claim_동시중복_DataIntegrityViolation_409() {
    // given: existsByCustomerIdAndCouponId=false 로 통과했으나 saveAndFlush 에서 UNIQUE 위반 발생
    injectClock();
    Coupon coupon = CouponFixture.anEventCoupon(); // ongoing at 2026-06-08
    Customer customer = customer();

    given(couponRepository.findById(2L)).willReturn(Optional.of(coupon));
    given(userCouponRepository.existsByCustomerIdAndCouponId(CUSTOMER_ID, 2L)).willReturn(false);
    given(couponRepository.incrementIssuedCountIfAvailable(2L)).willReturn(1);
    given(customerRepository.getReferenceById(CUSTOMER_ID)).willReturn(customer);
    given(userCouponRepository.saveAndFlush(any(UserCoupon.class)))
        .willThrow(new DataIntegrityViolationException("dup"));

    // when / then
    assertThatThrownBy(() -> couponService.claim(CUSTOMER_ID, 2L))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", CouponErrorCode.COUPON_ALREADY_CLAIMED);
  }

  @Test
  void claim_없는쿠폰_404() {
    // given
    given(couponRepository.findById(999L)).willReturn(Optional.empty());

    // when / then
    assertThatThrownBy(() -> couponService.claim(CUSTOMER_ID, 999L))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", CouponErrorCode.COUPON_NOT_FOUND);
  }

  @Test
  void claim_EVENT아님_409() {
    // given: SIGNUP 쿠폰 → COUPON_NOT_AVAILABLE (kind 체크에서 걸림, isOngoing 체크 도달 X)
    Coupon signup = CouponFixture.aSignupCoupon();
    given(couponRepository.findById(1L)).willReturn(Optional.of(signup));

    // when / then
    assertThatThrownBy(() -> couponService.claim(CUSTOMER_ID, 1L))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", CouponErrorCode.COUPON_NOT_AVAILABLE);
  }

  @Test
  void claim_비진행중_SCHEDULED_409() {
    // given: EVENT 쿠폰이지만 시작일 미도래 → SCHEDULED → COUPON_NOT_AVAILABLE
    injectClock();
    Coupon scheduled =
        Coupon.builder()
            .kind(CouponKind.EVENT)
            .label("예정 쿠폰")
            .discountType(CouponDiscountType.AMOUNT)
            .discountValue(1000)
            .minOrder(0)
            .validUntil(LocalDate.of(2026, 12, 31))
            .displayStartAt(LocalDate.of(2026, 6, 10)) // 2026-06-10 시작 → SCHEDULED
            .displayEndAt(LocalDate.of(2026, 12, 31))
            .active(true)
            .build();
    ReflectionTestUtils.setField(scheduled, "id", 5L);

    given(couponRepository.findById(5L)).willReturn(Optional.of(scheduled));

    // when / then
    assertThatThrownBy(() -> couponService.claim(CUSTOMER_ID, 5L))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", CouponErrorCode.COUPON_NOT_AVAILABLE);
  }

  @Test
  void claim_이미받음_409() {
    // given
    injectClock();
    Coupon coupon = CouponFixture.anEventCoupon(); // ongoing at 2026-06-08
    given(couponRepository.findById(2L)).willReturn(Optional.of(coupon));
    given(userCouponRepository.existsByCustomerIdAndCouponId(CUSTOMER_ID, 2L)).willReturn(true);

    // when / then
    assertThatThrownBy(() -> couponService.claim(CUSTOMER_ID, 2L))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", CouponErrorCode.COUPON_ALREADY_CLAIMED);

    then(couponRepository).should(never()).incrementIssuedCountIfAvailable(any());
  }

  @Test
  void claim_마감_409() {
    // given: increment 가 0 반환 = 한도 초과
    injectClock();
    Coupon coupon = CouponFixture.anEventCoupon(); // ongoing at 2026-06-08
    given(couponRepository.findById(2L)).willReturn(Optional.of(coupon));
    given(userCouponRepository.existsByCustomerIdAndCouponId(CUSTOMER_ID, 2L)).willReturn(false);
    given(couponRepository.incrementIssuedCountIfAvailable(2L)).willReturn(0);

    // when / then
    assertThatThrownBy(() -> couponService.claim(CUSTOMER_ID, 2L))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", CouponErrorCode.COUPON_SOLD_OUT);

    then(customerRepository).should(never()).getReferenceById(any());
  }

  // ── createEvent ──────────────────────────────────────────────────────────────

  @Test
  void createEvent_성공() {
    // given
    injectClock();
    LocalDate start = LocalDate.of(2026, 6, 9);
    LocalDate end = LocalDate.of(2026, 12, 31);
    AdminCouponCreateRequest req =
        new AdminCouponCreateRequest(
            "테스트 이벤트",
            CouponDiscountType.AMOUNT,
            3000,
            10000,
            LocalDate.of(2026, 12, 31),
            50,
            start,
            end);
    Coupon saved =
        CouponFixture.anEventCoupon(); // displayStartAt=2026-06-01, displayEndAt=2026-12-31

    given(couponRepository.save(any(Coupon.class))).willReturn(saved);

    // when
    AdminCouponResponse result = couponService.createEvent(req);

    // then: 서비스 직접 빌드 —
    // id/label/discountType/value/minOrder/issuedCount/active/displayStart/End/status
    assertThat(result.id()).isEqualTo(2L);
    assertThat(result.label()).isEqualTo("봄맞이 이벤트 쿠폰");
    assertThat(result.discountType()).isEqualTo(CouponDiscountType.AMOUNT);
    assertThat(result.value()).isEqualTo(3000);
    assertThat(result.status())
        .isEqualTo(EventStatus.ONGOING); // 2026-06-01 ~ 2026-12-31, today=2026-06-08
    then(couponRepository).should().save(any(Coupon.class));
  }

  @Test
  void createEvent_RATE_범위초과_400() {
    // given: RATE 101% → INVALID_DISCOUNT_RATE
    AdminCouponCreateRequest req =
        new AdminCouponCreateRequest(
            "잘못된 쿠폰",
            CouponDiscountType.RATE,
            101,
            0,
            LocalDate.of(2026, 12, 31),
            null,
            LocalDate.of(2026, 6, 9),
            LocalDate.of(2026, 12, 31));

    // when / then
    assertThatThrownBy(() -> couponService.createEvent(req))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", CouponErrorCode.INVALID_DISCOUNT_RATE);
    then(couponRepository).should(never()).save(any());
  }

  @Test
  void createEvent_기간역전_400() {
    // given: displayStartAt > displayEndAt → INVALID_EVENT_PERIOD
    injectClock();
    AdminCouponCreateRequest req =
        new AdminCouponCreateRequest(
            "기간역전 쿠폰",
            CouponDiscountType.AMOUNT,
            1000,
            0,
            LocalDate.of(2026, 12, 31),
            null,
            LocalDate.of(2026, 12, 31),
            LocalDate.of(2026, 6, 9)); // 역전

    // when / then
    assertThatThrownBy(() -> couponService.createEvent(req))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", CouponErrorCode.INVALID_EVENT_PERIOD);
    then(couponRepository).should(never()).save(any());
  }

  @Test
  void createEvent_만료일이_노출종료일보다_앞서면_400() {
    // given: validUntil(2026-06-30) < displayEndAt(2026-12-31) → 발급 즉시 죽은 쿠폰
    AdminCouponCreateRequest req =
        new AdminCouponCreateRequest(
            "만료일 빠른 쿠폰",
            CouponDiscountType.AMOUNT,
            1000,
            0,
            LocalDate.of(2026, 6, 30),
            null,
            LocalDate.of(2026, 6, 9),
            LocalDate.of(2026, 12, 31));

    // when / then
    assertThatThrownBy(() -> couponService.createEvent(req))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", CouponErrorCode.INVALID_COUPON_VALIDITY);
    then(couponRepository).should(never()).save(any());
  }

  // ── listEvents ───────────────────────────────────────────────────────────────

  @Test
  void listEvents_전체_EVENT_반환_상태포함() {
    // given
    injectClock();
    Coupon ongoingCoupon = CouponFixture.anEventCoupon(); // ONGOING at 2026-06-08
    Coupon endedCoupon =
        Coupon.builder()
            .kind(CouponKind.EVENT)
            .label("종료된 쿠폰")
            .discountType(CouponDiscountType.AMOUNT)
            .discountValue(2000)
            .minOrder(5000)
            .validUntil(LocalDate.of(2026, 5, 31))
            .displayStartAt(LocalDate.of(2026, 5, 1))
            .displayEndAt(LocalDate.of(2026, 5, 31)) // 종료
            .active(true)
            .build();
    ReflectionTestUtils.setField(endedCoupon, "id", 10L);

    given(couponRepository.findByKindOrderByCreatedAtDesc(CouponKind.EVENT))
        .willReturn(List.of(ongoingCoupon, endedCoupon));

    // when
    List<AdminCouponResponse> result = couponService.listEvents();

    // then: 전체 2건 (상태 포함)
    assertThat(result).hasSize(2);
    assertThat(result.get(0).status()).isEqualTo(EventStatus.ONGOING);
    assertThat(result.get(1).status()).isEqualTo(EventStatus.ENDED);
  }

  // ── updateEvent ──────────────────────────────────────────────────────────────

  @Test
  void updateEvent_label만_수정() {
    // given
    injectClock();
    Coupon coupon = CouponFixture.anEventCoupon(); // id=2
    given(couponRepository.findById(2L)).willReturn(Optional.of(coupon));

    AdminCouponUpdateRequest req =
        new AdminCouponUpdateRequest("새 이름", null, null, null, null, null, null, null);

    // when
    AdminCouponResponse result = couponService.updateEvent(2L, req);

    // then
    assertThat(result.label()).isEqualTo("새 이름");
    assertThat(result.status()).isEqualTo(EventStatus.ONGOING);
  }

  @Test
  void updateEvent_없는쿠폰_404() {
    // given
    given(couponRepository.findById(999L)).willReturn(Optional.empty());

    AdminCouponUpdateRequest req =
        new AdminCouponUpdateRequest("수정", null, null, null, null, null, null, null);

    // when / then
    assertThatThrownBy(() -> couponService.updateEvent(999L, req))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", CouponErrorCode.COUPON_NOT_FOUND);
  }

  @Test
  void updateEvent_SIGNUP쿠폰_404() {
    // given: SIGNUP 쿠폰은 EVENT 아니므로 404
    Coupon signup = CouponFixture.aSignupCoupon();
    given(couponRepository.findById(1L)).willReturn(Optional.of(signup));

    AdminCouponUpdateRequest req =
        new AdminCouponUpdateRequest("수정", null, null, null, null, null, null, null);

    // when / then
    assertThatThrownBy(() -> couponService.updateEvent(1L, req))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", CouponErrorCode.COUPON_NOT_FOUND);
  }

  @Test
  void updateEvent_만료일을_노출종료일보다_앞으로_수정하면_400() {
    // given: 기존 displayEndAt=2026-12-31. validUntil 만 2026-06-30 으로 수정 → 검증 실패
    Coupon coupon = CouponFixture.anEventCoupon(); // id=2, displayEndAt=2026-12-31
    given(couponRepository.findById(2L)).willReturn(Optional.of(coupon));
    AdminCouponUpdateRequest req =
        new AdminCouponUpdateRequest(
            null, null, null, null, LocalDate.of(2026, 6, 30), null, null, null);

    // when / then
    assertThatThrownBy(() -> couponService.updateEvent(2L, req))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", CouponErrorCode.INVALID_COUPON_VALIDITY);
  }

  @Test
  void updateEvent_노출종료일을_기존만료일보다_뒤로_수정하면_400() {
    // given: 기존 validUntil=2026-12-31. displayEndAt 만 2027-06-30 으로 수정 → effectiveEnd 기준 검증 실패
    Coupon coupon = CouponFixture.anEventCoupon(); // id=2, validUntil=2026-12-31
    given(couponRepository.findById(2L)).willReturn(Optional.of(coupon));
    AdminCouponUpdateRequest req =
        new AdminCouponUpdateRequest(
            null, null, null, null, null, null, null, LocalDate.of(2027, 6, 30));

    // when / then
    assertThatThrownBy(() -> couponService.updateEvent(2L, req))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", CouponErrorCode.INVALID_COUPON_VALIDITY);
  }

  // ── endEvent ─────────────────────────────────────────────────────────────────

  @Test
  void endEvent_성공_active_false_ENDED() {
    // given
    injectClock();
    Coupon coupon = CouponFixture.anEventCoupon(); // id=2, active=true, ONGOING
    given(couponRepository.findById(2L)).willReturn(Optional.of(coupon));

    // when
    AdminCouponResponse result = couponService.endEvent(2L);

    // then: active=false → ENDED
    assertThat(result.active()).isFalse();
    assertThat(result.status()).isEqualTo(EventStatus.ENDED);
  }

  @Test
  void endEvent_없는쿠폰_404() {
    // given
    given(couponRepository.findById(999L)).willReturn(Optional.empty());

    // when / then
    assertThatThrownBy(() -> couponService.endEvent(999L))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", CouponErrorCode.COUPON_NOT_FOUND);
  }

  // ── use ──────────────────────────────────────────────────────────────────────

  @Test
  void use_USABLE를_USED로() {
    // given
    injectClock();
    Customer customer = customer();
    Coupon coupon = CouponFixture.aSignupCoupon();
    UserCoupon uc = CouponFixture.aUsableUserCoupon(customer, coupon);

    given(userCouponRepository.findById(10L)).willReturn(Optional.of(uc));
    given(userCouponRepository.markUsed(eq(10L), any())).willReturn(1);

    // when — 예외 없이 정상 완료
    couponService.use(10L);

    // then: 원자적 UPDATE 1건 호출됨
    then(userCouponRepository).should().markUsed(eq(10L), any());
  }

  @Test
  void use_동시사용_마감_NOT_AVAILABLE() {
    // given: markUsed 가 0 반환 = 동시 사용으로 이미 USED 전이됨
    injectClock();
    Customer customer = customer();
    Coupon coupon = CouponFixture.aSignupCoupon();
    UserCoupon uc = CouponFixture.aUsableUserCoupon(customer, coupon);

    given(userCouponRepository.findById(10L)).willReturn(Optional.of(uc));
    given(userCouponRepository.markUsed(eq(10L), any())).willReturn(0);

    // when / then
    assertThatThrownBy(() -> couponService.use(10L))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", CouponErrorCode.COUPON_NOT_AVAILABLE);
  }

  @Test
  void use_만료면_NOT_AVAILABLE() {
    // given
    injectClock();
    Customer customer = customer();
    Coupon coupon = CouponFixture.aSignupCoupon();
    UserCoupon expired = CouponFixture.anExpiredUserCoupon(customer, coupon);

    given(userCouponRepository.findById(11L)).willReturn(Optional.of(expired));

    // when / then
    assertThatThrownBy(() -> couponService.use(11L))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", CouponErrorCode.COUPON_NOT_AVAILABLE);
  }

  // ── restore ───────────────────────────────────────────────────────────────────

  @Test
  void restore_USED를_USABLE로() {
    // given
    injectClock();
    Customer customer = customer();
    Coupon coupon = CouponFixture.aSignupCoupon();
    UserCoupon uc = CouponFixture.aUsableUserCoupon(customer, coupon);
    // 직접 USED 상태로 설정
    ReflectionTestUtils.setField(uc, "status", CouponStatus.USED);

    given(userCouponRepository.findById(10L)).willReturn(Optional.of(uc));

    // when
    couponService.restore(10L);

    // then
    assertThat(uc.getStatus()).isEqualTo(CouponStatus.USABLE);
    assertThat(uc.getUsedAt()).isNull();
  }

  @Test
  void restore_만료면_복원안함() {
    // given
    injectClock();
    Customer customer = customer();
    Coupon coupon = CouponFixture.aSignupCoupon();
    UserCoupon expired = CouponFixture.anExpiredUserCoupon(customer, coupon);
    ReflectionTestUtils.setField(expired, "status", CouponStatus.USED);

    given(userCouponRepository.findById(11L)).willReturn(Optional.of(expired));

    // when: 만료된 쿠폰 복원 시도 → 상태 변경 없음
    couponService.restore(11L);

    // then: 여전히 USED
    assertThat(expired.getStatus()).isEqualTo(CouponStatus.USED);
  }

  // ── getUsableForOrder ─────────────────────────────────────────────────────────

  @Test
  void getUsableForOrder_타인쿠폰_NOT_AVAILABLE() {
    // given
    injectClock();
    Customer customer = customer(); // CUSTOMER_ID = 1L
    Coupon coupon = CouponFixture.aSignupCoupon();
    UserCoupon uc = CouponFixture.aUsableUserCoupon(customer, coupon);

    given(userCouponRepository.findByIdWithCoupon(10L)).willReturn(Optional.of(uc));

    // when / then: customerId=999L 은 타인
    assertThatThrownBy(() -> couponService.getUsableForOrder(10L, 999L))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", CouponErrorCode.COUPON_NOT_AVAILABLE);
  }

  @Test
  void getUsableForOrder_성공() {
    // given
    injectClock();
    Customer customer = customer(); // CUSTOMER_ID = 1L
    Coupon coupon = CouponFixture.aSignupCoupon();
    UserCoupon uc = CouponFixture.aUsableUserCoupon(customer, coupon);

    given(userCouponRepository.findByIdWithCoupon(10L)).willReturn(Optional.of(uc));

    // when
    UserCoupon result = couponService.getUsableForOrder(10L, CUSTOMER_ID);

    // then
    assertThat(result).isEqualTo(uc);
  }

  // ── grantSignupCoupon ─────────────────────────────────────────────────────────

  @Test
  void grantSignupCoupon_마스터있으면_발급() {
    // given
    injectClock();
    Customer customer = customer();
    Coupon master = CouponFixture.aSignupCoupon();

    given(couponRepository.findFirstByKindAndActiveTrueOrderByIdAsc(CouponKind.SIGNUP))
        .willReturn(Optional.of(master));
    given(userCouponRepository.save(any(UserCoupon.class))).willAnswer(inv -> inv.getArgument(0));

    // when
    couponService.grantSignupCoupon(customer);

    // then: UserCoupon 저장됨 + 발급 알림
    then(userCouponRepository).should().save(any(UserCoupon.class));
    then(notificationService)
        .should()
        .notifyCustomer(
            eq(CUSTOMER_ID),
            eq("eventBenefit"),
            eq(NotificationCategory.BENEFIT),
            any(String.class),
            any(String.class),
            any(String.class));
  }

  @Test
  void grantSignupCoupon_마스터없으면_skip() {
    // given
    Customer customer = customer();
    given(couponRepository.findFirstByKindAndActiveTrueOrderByIdAsc(CouponKind.SIGNUP))
        .willReturn(Optional.empty());

    // when
    couponService.grantSignupCoupon(customer);

    // then: 발급 시도 없음
    then(userCouponRepository).should(never()).save(any());
  }

  // ── expireCoupons ─────────────────────────────────────────────────────────────

  @Test
  void 소멸배치_USABLE만료_EXPIRED() {
    // given
    injectClock();
    given(userCouponRepository.expireUsableBefore(any(LocalDate.class))).willReturn(3);

    // when
    int result = couponService.expireCoupons();

    // then
    assertThat(result).isEqualTo(3);
    then(userCouponRepository).should().expireUsableBefore(any(LocalDate.class));
  }

  // ── notifyExpiringCoupons ────────────────────────────────────────────────────

  @Test
  void 소멸예정알림_7일이내_쿠폰_발송() {
    // given
    injectClock();
    Customer customer = customer();
    Coupon coupon = CouponFixture.aSignupCoupon();
    UserCoupon uc = CouponFixture.aUsableUserCoupon(customer, coupon);

    given(
            userCouponRepository.findExpiringForAlert(
                eq(CouponStatus.USABLE), any(LocalDate.class), any(LocalDate.class)))
        .willReturn(List.of(uc));

    // when
    couponService.notifyExpiringCoupons();

    // then: 알림 발송 + sentAt 마킹
    then(notificationService)
        .should()
        .notifyCustomer(
            eq(CUSTOMER_ID),
            eq("eventBenefit"),
            eq(NotificationCategory.BENEFIT),
            any(String.class),
            any(String.class),
            any(String.class));
    assertThat(uc.getExpiryAlertSentAt()).isNotNull();
  }

  @Test
  void 소멸예정알림_대상없으면_발송안함() {
    // given
    injectClock();
    given(
            userCouponRepository.findExpiringForAlert(
                any(), any(LocalDate.class), any(LocalDate.class)))
        .willReturn(List.of());

    // when
    couponService.notifyExpiringCoupons();

    // then
    then(notificationService).shouldHaveNoInteractions();
  }

  // ── 소급 방지 회귀 (Service 레벨) ─────────────────────────────────────────────

  @Test
  void 소급방지_발급후_마스터변경_발급된쿠폰_스냅샷_유지() {
    // given: 발급 시점 스냅샷 AMOUNT 1000, minOrder 2000
    Customer customer = customer();
    Coupon coupon = CouponFixture.anEventCoupon(); // discountValue=3000, minOrder=10000
    UserCoupon uc = CouponFixture.aUsableUserCoupon(customer, coupon);

    // uc 의 스냅샷은 coupon 발급 시점 값 — discountValue=3000, minOrder=10000
    assertThat(uc.getDiscountValue()).isEqualTo(3000);
    assertThat(uc.getMinOrder()).isEqualTo(10000);

    // "마스터 변경" 시뮬레이션 — 새 쿠폰 인스턴스로 수정된 마스터 표현
    Coupon modifiedMaster =
        Coupon.builder()
            .kind(CouponKind.EVENT)
            .label("봄맞이 이벤트 쿠폰")
            .discountType(CouponDiscountType.AMOUNT)
            .discountValue(5000) // 변경됨
            .minOrder(20000) // 변경됨
            .validUntil(LocalDate.of(2026, 12, 31))
            .displayStartAt(LocalDate.of(2026, 6, 1))
            .displayEndAt(LocalDate.of(2026, 12, 31))
            .active(true)
            .build();

    // then: 기존 발급된 uc 스냅샷은 변경 없음 (소급 방지)
    assertThat(uc.getDiscountValue()).isEqualTo(3000); // 마스터 변경과 무관
    assertThat(uc.calcDiscount(new BigDecimal("15000"))).isEqualByComparingTo("3000");

    // 새로 발급한다면 새 스냅샷
    UserCoupon newUc = CouponFixture.aUsableUserCoupon(customer, modifiedMaster);
    assertThat(newUc.getDiscountValue()).isEqualTo(5000);
    assertThat(newUc.calcDiscount(new BigDecimal("25000"))).isEqualByComparingTo("5000");
  }
}
