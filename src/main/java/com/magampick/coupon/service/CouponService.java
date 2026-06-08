package com.magampick.coupon.service;

import com.magampick.coupon.domain.Coupon;
import com.magampick.coupon.domain.CouponKind;
import com.magampick.coupon.domain.CouponStatus;
import com.magampick.coupon.domain.UserCoupon;
import com.magampick.coupon.dto.AdminCouponCreateRequest;
import com.magampick.coupon.dto.AdminCouponResponse;
import com.magampick.coupon.dto.CouponEventResponse;
import com.magampick.coupon.dto.CouponResponse;
import com.magampick.coupon.exception.CouponErrorCode;
import com.magampick.coupon.mapper.CouponMapper;
import com.magampick.coupon.repository.CouponRepository;
import com.magampick.coupon.repository.UserCouponRepository;
import com.magampick.customer.domain.Customer;
import com.magampick.customer.repository.CustomerRepository;
import com.magampick.global.exception.BusinessException;
import com.magampick.notification.domain.NotificationCategory;
import com.magampick.notification.service.NotificationService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 쿠폰 발급·조회 서비스. */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CouponService {

  private final CouponRepository couponRepository;
  private final UserCouponRepository userCouponRepository;
  private final CustomerRepository customerRepository;
  private final CouponMapper couponMapper;
  private final NotificationService notificationService;
  private final Clock clock;

  /**
   * 소비자 쿠폰함 조회. 만료 방어판정(조회 시 USABLE + 만료일 경과 → EXPIRED 표시).
   *
   * @param customerId 소비자 ID
   * @return 쿠폰함 목록 (최근 발급순)
   */
  public List<CouponResponse> getMyCoupons(Long customerId) {
    List<UserCoupon> userCoupons = userCouponRepository.findByCustomerIdWithCoupon(customerId);
    LocalDate today = LocalDate.now(clock);
    return userCoupons.stream()
        .map(
            uc -> {
              CouponStatus displayStatus =
                  uc.isExpiredAt(today) ? CouponStatus.EXPIRED : uc.getStatus();
              return couponMapper.toResponse(uc, displayStatus);
            })
        .toList();
  }

  /**
   * 이벤트 쿠폰 목록 조회. 소비자의 기 수령 쿠폰 ID 집합으로 claimed 플래그를 설정한다.
   *
   * @param customerId 소비자 ID
   * @return 활성 이벤트 쿠폰 목록
   */
  public List<CouponEventResponse> getEvents(Long customerId) {
    Set<Long> claimedIds = userCouponRepository.findClaimedCouponIdsByCustomerId(customerId);
    List<Coupon> events = couponRepository.findByKindAndActiveTrue(CouponKind.EVENT);
    return events.stream()
        .map(coupon -> couponMapper.toEventResponse(coupon, claimedIds.contains(coupon.getId())))
        .toList();
  }

  /**
   * 이벤트 쿠폰 발급 (선착순 + 1인 1회).
   *
   * <ol>
   *   <li>쿠폰 존재 확인
   *   <li>EVENT + 활성 확인
   *   <li>1인 1회 중복 확인
   *   <li>선착순 원자 카운트 증가 (issueLimit 초과 시 0 반환)
   *   <li>UserCoupon 저장 후 반환
   * </ol>
   *
   * @param customerId 소비자 ID
   * @param couponId 쿠폰 마스터 ID
   * @return 발급된 쿠폰 인스턴스
   */
  @Transactional
  public CouponResponse claim(Long customerId, Long couponId) {
    Coupon coupon =
        couponRepository
            .findById(couponId)
            .orElseThrow(() -> new BusinessException(CouponErrorCode.COUPON_NOT_FOUND));

    if (coupon.getKind() != CouponKind.EVENT || !coupon.isActive()) {
      throw new BusinessException(CouponErrorCode.COUPON_NOT_AVAILABLE);
    }
    if (userCouponRepository.existsByCustomerIdAndCouponId(customerId, couponId)) {
      throw new BusinessException(CouponErrorCode.COUPON_ALREADY_CLAIMED);
    }

    int updated = couponRepository.incrementIssuedCountIfAvailable(couponId);
    if (updated == 0) {
      throw new BusinessException(CouponErrorCode.COUPON_SOLD_OUT);
    }

    Customer customer = customerRepository.getReferenceById(customerId);

    LocalDate expiresAt = coupon.getValidUntil();
    UserCoupon userCoupon;
    try {
      userCoupon =
          userCouponRepository.saveAndFlush(
              UserCoupon.builder()
                  .customer(customer)
                  .coupon(coupon)
                  .status(CouponStatus.USABLE)
                  .expiresAt(expiresAt)
                  .issuedAt(LocalDateTime.now(clock))
                  .build());
    } catch (DataIntegrityViolationException e) {
      throw new BusinessException(CouponErrorCode.COUPON_ALREADY_CLAIMED);
    }

    log.info("쿠폰 발급됨. customerId={}, couponId={}", customerId, couponId);
    notifyIssued(customerId, coupon.getLabel(), userCoupon.getExpiresAt());
    return couponMapper.toResponse(userCoupon, CouponStatus.USABLE);
  }

  /**
   * 관리자 이벤트 쿠폰 생성. RATE 할인이면 value 1~100 범위 검증.
   *
   * @param req 쿠폰 생성 요청
   * @return 생성된 쿠폰 마스터
   */
  @Transactional
  public AdminCouponResponse createEvent(AdminCouponCreateRequest req) {
    if (req.discountType() == com.magampick.coupon.domain.CouponDiscountType.RATE
        && (req.value() < 1 || req.value() > 100)) {
      throw new BusinessException(CouponErrorCode.INVALID_DISCOUNT_RATE);
    }
    Coupon coupon =
        couponRepository.save(
            Coupon.builder()
                .kind(CouponKind.EVENT)
                .label(req.label())
                .discountType(req.discountType())
                .discountValue(req.value())
                .minOrder(req.minOrder())
                .validUntil(req.validUntil())
                .validityDays(null)
                .issueLimit(req.issueLimit())
                .active(true)
                .build());
    return couponMapper.toAdminResponse(coupon);
  }

  /**
   * 체크아웃 시 쿠폰 사용 처리. 만료 확인 후 원자적 USABLE→USED 전이 (동시 사용 경쟁 조건 방지).
   *
   * @param userCouponId 사용할 UserCoupon ID
   * @throws BusinessException COUPON_NOT_FOUND / COUPON_NOT_AVAILABLE
   */
  @Transactional
  public void use(Long userCouponId) {
    UserCoupon uc =
        userCouponRepository
            .findById(userCouponId)
            .orElseThrow(() -> new BusinessException(CouponErrorCode.COUPON_NOT_FOUND));
    if (uc.isExpiredAt(LocalDate.now(clock))) {
      throw new BusinessException(CouponErrorCode.COUPON_NOT_AVAILABLE);
    }
    int updated = userCouponRepository.markUsed(userCouponId, LocalDateTime.now(clock));
    if (updated == 0) { // 동시 사용 / 이미 USED
      throw new BusinessException(CouponErrorCode.COUPON_NOT_AVAILABLE);
    }
  }

  /**
   * 취소/환불 시 쿠폰 복원. 만료된 쿠폰(expiresAt 경과)은 복원하지 않는다. isExpiredAt 은 USABLE 상태에서만 true 를 반환하므로, USED
   * 상태의 만료 여부는 expiresAt 직접 비교.
   *
   * @param userCouponId 복원할 UserCoupon ID
   * @throws BusinessException COUPON_NOT_FOUND
   */
  @Transactional
  public void restore(Long userCouponId) {
    UserCoupon uc =
        userCouponRepository
            .findById(userCouponId)
            .orElseThrow(() -> new BusinessException(CouponErrorCode.COUPON_NOT_FOUND));
    if (uc.getExpiresAt().isBefore(LocalDate.now(clock))) {
      return; // 만료된 쿠폰은 복원 X
    }
    if (uc.getStatus() == CouponStatus.USED) {
      uc.restore();
    }
  }

  /**
   * 체크아웃 쿠폰 검증 조회. coupon 페치 포함. 타인 쿠폰 / 사용 불가 쿠폰은 예외.
   *
   * @param userCouponId UserCoupon ID
   * @param customerId 요청자 소비자 ID
   * @return 검증된 UserCoupon (coupon 로드 완료)
   * @throws BusinessException COUPON_NOT_FOUND / COUPON_NOT_AVAILABLE
   */
  @Transactional(readOnly = true)
  public UserCoupon getUsableForOrder(Long userCouponId, Long customerId) {
    UserCoupon uc =
        userCouponRepository
            .findByIdWithCoupon(userCouponId)
            .orElseThrow(() -> new BusinessException(CouponErrorCode.COUPON_NOT_FOUND));
    if (!uc.getCustomer().getId().equals(customerId)) {
      throw new BusinessException(CouponErrorCode.COUPON_NOT_AVAILABLE);
    }
    if (uc.getStatus() != CouponStatus.USABLE || uc.isExpiredAt(LocalDate.now(clock))) {
      throw new BusinessException(CouponErrorCode.COUPON_NOT_AVAILABLE);
    }
    return uc;
  }

  /**
   * 만료일 경과 USABLE 쿠폰 소멸 배치. expiresAt < today 인 USABLE 쿠폰을 EXPIRED 로 일괄 전이.
   *
   * @return 처리된 쿠폰 수
   */
  @Transactional
  public int expireCoupons() {
    int n = userCouponRepository.expireUsableBefore(LocalDate.now(clock));
    log.info("쿠폰 소멸 배치 완료. 처리 건수={}", n);
    return n;
  }

  /**
   * 만료 7일 전 알림 발송. USABLE 쿠폰 중 today~today+7일 만료이고 미발송인 쿠폰에 알림 1건씩.
   *
   * <p>알림 1건 실패가 다른 쿠폰에 영향을 주지 않도록 쿠폰 단위로 try-catch.
   */
  @Transactional
  public void notifyExpiringCoupons() {
    LocalDate today = LocalDate.now(clock);
    LocalDate sevenDaysLater = today.plusDays(7);
    List<UserCoupon> targets =
        userCouponRepository.findExpiringForAlert(CouponStatus.USABLE, today, sevenDaysLater);
    if (targets.isEmpty()) {
      return;
    }
    LocalDateTime now = LocalDateTime.now(clock);
    for (UserCoupon uc : targets) {
      try {
        notificationService.notifyCustomer(
            uc.getCustomer().getId(),
            "eventBenefit",
            NotificationCategory.BENEFIT,
            "쿠폰이 곧 만료돼요",
            "'" + uc.getCoupon().getLabel() + "' 쿠폰이 7일 내에 만료됩니다. 지금 사용해보세요!",
            "/coupons");
        uc.markExpiryAlertSent(now);
      } catch (Exception e) {
        log.warn("쿠폰 소멸 예정 알림 발송 실패. userCouponId={}", uc.getId(), e);
      }
    }
  }

  /** 쿠폰 발급 알림 공통 발송. FCM 실패는 로그만. */
  private void notifyIssued(Long customerId, String couponLabel, LocalDate expiresAt) {
    try {
      notificationService.notifyCustomer(
          customerId,
          "eventBenefit",
          NotificationCategory.BENEFIT,
          "쿠폰이 발급됐어요",
          "'" + couponLabel + "' 쿠폰이 발급됐습니다. " + expiresAt + "까지 사용 가능해요!",
          "/coupons");
    } catch (Exception e) {
      log.warn("쿠폰 발급 알림 발송 실패. customerId={}", customerId, e);
    }
  }

  /**
   * 가입 축하 쿠폰 자동 발급. SIGNUP 마스터가 없으면 경고 후 건너뜀.
   *
   * @param customer 이미 저장된 소비자 엔티티
   */
  @Transactional
  public void grantSignupCoupon(Customer customer) {
    couponRepository
        .findFirstByKindAndActiveTrueOrderByIdAsc(CouponKind.SIGNUP)
        .ifPresentOrElse(
            master -> {
              LocalDate expiresAt = LocalDate.now(clock).plusDays(master.getValidityDays());
              userCouponRepository.save(
                  UserCoupon.builder()
                      .customer(customer)
                      .coupon(master)
                      .status(CouponStatus.USABLE)
                      .expiresAt(expiresAt)
                      .issuedAt(LocalDateTime.now(clock))
                      .build());
              notifyIssued(customer.getId(), master.getLabel(), expiresAt);
            },
            () -> log.warn("가입 축하 쿠폰 마스터 없음 — 발급 건너뜀. customerId={}", customer.getId()));
  }
}
