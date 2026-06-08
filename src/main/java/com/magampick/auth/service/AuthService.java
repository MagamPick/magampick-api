package com.magampick.auth.service;

import com.magampick.address.service.AddressService;
import com.magampick.admin.domain.Admin;
import com.magampick.admin.repository.AdminRepository;
import com.magampick.auth.domain.CustomerOAuthAccount;
import com.magampick.auth.domain.OAuthProviderType;
import com.magampick.auth.dto.AdminLoginRequest;
import com.magampick.auth.dto.CustomerSignupRequest;
import com.magampick.auth.dto.EmailAvailabilityResponse;
import com.magampick.auth.dto.IssuedTokens;
import com.magampick.auth.dto.KakaoLoginRequest;
import com.magampick.auth.dto.LoginRequest;
import com.magampick.auth.dto.PasswordChangeRequest;
import com.magampick.auth.dto.PasswordResetConfirmRequest;
import com.magampick.auth.dto.PasswordResetVerifyRequest;
import com.magampick.auth.dto.PasswordResetVerifyResponse;
import com.magampick.auth.dto.SellerSignupRequest;
import com.magampick.auth.dto.SocialSignupRequest;
import com.magampick.auth.dto.TokenResponse;
import com.magampick.auth.exception.AuthErrorCode;
import com.magampick.auth.oauth.OAuthProvider;
import com.magampick.auth.oauth.OAuthUserInfo;
import com.magampick.auth.repository.CustomerOAuthAccountRepository;
import com.magampick.auth.repository.PasswordResetStore;
import com.magampick.auth.repository.SocialAuthStore;
import com.magampick.coupon.service.CouponService;
import com.magampick.customer.domain.Customer;
import com.magampick.customer.exception.CustomerErrorCode;
import com.magampick.customer.repository.CustomerRepository;
import com.magampick.global.exception.BusinessException;
import com.magampick.global.exception.CommonErrorCode;
import com.magampick.global.security.Role;
import com.magampick.phone.service.PhoneVerificationService;
import com.magampick.seller.domain.Seller;
import com.magampick.seller.exception.SellerErrorCode;
import com.magampick.seller.repository.SellerRepository;
import com.magampick.store.service.PreparedStoreRegistration;
import com.magampick.store.service.StoreService;
import com.magampick.terms.service.TermService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

  private static final int NICKNAME_MIN = 2;
  private static final int NICKNAME_MAX = 12;
  private static final int SELLER_NAME_MIN = 2;
  private static final int SELLER_NAME_MAX = 20;

  private final AdminRepository adminRepository;
  private final CustomerRepository customerRepository;
  private final SellerRepository sellerRepository;
  private final CustomerOAuthAccountRepository customerOAuthAccountRepository;
  private final RefreshTokenService refreshTokenService;
  private final PasswordValidator passwordValidator;
  private final PasswordEncoder passwordEncoder;
  private final OAuthProvider kakaoOAuthProvider;
  private final PhoneVerificationService phoneVerificationService;
  private final TermService termService;
  private final AddressService addressService;
  private final SocialAuthStore socialAuthStore;
  private final PasswordResetStore passwordResetStore;
  private final StoreService storeService;
  private final TransactionTemplate transactionTemplate;
  private final CouponService couponService;

  public EmailAvailabilityResponse checkEmailAvailability(Role role, String email) {
    boolean exists =
        switch (role) {
          case CUSTOMER -> customerRepository.existsByEmail(email);
          case SELLER -> sellerRepository.existsByEmail(email);
          case ADMIN -> throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        };
    if (exists) {
      throw new BusinessException(AuthErrorCode.EMAIL_ALREADY_EXISTS);
    }
    return new EmailAvailabilityResponse(true);
  }

  /**
   * 소비자 회원가입 오케스트레이션 (5단계 통합). 한 트랜잭션으로 본인인증 토큰 소비 → customers 생성 → 약관 동의 기록 → 기본 주소 생성 → 토큰 발급.
   * 본인인증 토큰 소비(Redis 삭제)는 입력 검증을 모두 통과한 뒤 수행한다.
   */
  @Transactional
  public IssuedTokens signupCustomer(CustomerSignupRequest request) {
    if (customerRepository.existsByEmail(request.email())) {
      throw new BusinessException(AuthErrorCode.EMAIL_ALREADY_EXISTS);
    }
    passwordValidator.validate(request.password());
    validateNickname(request.nickname());
    if (request.address() == null) {
      throw new BusinessException(AuthErrorCode.DEFAULT_ADDRESS_REQUIRED);
    }

    String verifiedPhone = consumePhoneVerification(request.verificationToken(), request.phone());

    Customer customer =
        customerRepository.save(
            Customer.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .nickname(request.nickname())
                .phone(verifiedPhone)
                .phoneVerifiedAt(LocalDateTime.now())
                .build());

    termService.recordAgreements(customer, request.agreedTermIds());
    addressService.create(customer.getId(), request.address());
    couponService.grantSignupCoupon(customer);

    log.info("소비자 회원가입 완료. customerId={}", customer.getId());
    return refreshTokenService.issueTokens(customer.getId(), Role.CUSTOMER);
  }

  @Transactional
  public IssuedTokens loginCustomer(LoginRequest request) {
    Customer customer =
        customerRepository.findByEmail(request.email()).orElseThrow(this::invalidCredentials);
    if (customer.isDeleted() || customer.getPasswordHash() == null) {
      throw invalidCredentials();
    }
    if (!passwordEncoder.matches(request.password(), customer.getPasswordHash())) {
      throw invalidCredentials();
    }
    log.info("소비자 로그인 성공. customerId={}", customer.getId());
    return refreshTokenService.issueTokens(customer.getId(), Role.CUSTOMER);
  }

  public IssuedTokens signupSeller(SellerSignupRequest request, MultipartFile image) {
    if (sellerRepository.existsByEmail(request.email())) {
      throw new BusinessException(AuthErrorCode.EMAIL_ALREADY_EXISTS);
    }
    passwordValidator.validate(request.password());
    validateSellerName(request.ownerName());

    PreparedStoreRegistration prepared =
        storeService.prepareStoreRegistration(request.store(), image);
    String verifiedPhone = consumePhoneVerification(request.verificationToken(), request.phone());

    try {
      return transactionTemplate.execute(
          status -> {
            Seller seller =
                sellerRepository.save(
                    Seller.builder()
                        .email(request.email())
                        .passwordHash(passwordEncoder.encode(request.password()))
                        .ownerName(request.ownerName())
                        .phone(verifiedPhone)
                        .phoneVerifiedAt(LocalDateTime.now())
                        .build());
            termService.recordSellerAgreements(seller, request.agreedTermIds());
            storeService.createStore(seller, prepared);

            log.info("사장 회원가입 완료. sellerId={}", seller.getId());
            return refreshTokenService.issueTokens(seller.getId(), Role.SELLER);
          });
    } catch (RuntimeException e) {
      storeService.deletePreparedImageBestEffort(prepared);
      throw e;
    }
  }

  @Transactional
  public IssuedTokens loginSeller(LoginRequest request) {
    Seller seller =
        sellerRepository.findByEmail(request.email()).orElseThrow(this::invalidCredentials);
    if (seller.isDeleted()) {
      throw invalidCredentials();
    }
    if (!passwordEncoder.matches(request.password(), seller.getPasswordHash())) {
      throw invalidCredentials();
    }
    log.info("사장 로그인 성공. sellerId={}", seller.getId());
    return refreshTokenService.issueTokens(seller.getId(), Role.SELLER);
  }

  @Transactional
  public IssuedTokens loginAdmin(AdminLoginRequest request) {
    Admin admin =
        adminRepository.findByUsername(request.username()).orElseThrow(this::invalidCredentials);
    if (!passwordEncoder.matches(request.password(), admin.getPasswordHash())) {
      throw invalidCredentials();
    }
    log.info("관리자 로그인 성공. adminId={}", admin.getId());
    return refreshTokenService.issueTokens(admin.getId(), Role.ADMIN);
  }

  /** 카카오 1단계 — 인가코드로 카카오 정보 조회 후 기존/신규 분기. 기존은 즉시 토큰, 신규는 소셜 토큰 발급(가입 보류). */
  @Transactional
  public KakaoLoginResult kakaoLogin(KakaoLoginRequest request) {
    OAuthUserInfo userInfo =
        kakaoOAuthProvider.fetchUserInfo(request.authorizationCode(), request.redirectUri());

    return customerOAuthAccountRepository
        .findByProviderAndProviderUserId(OAuthProviderType.KAKAO, userInfo.providerUserId())
        .map(account -> existingLogin(account.getCustomer()))
        .orElseGet(() -> newSignupPending(userInfo));
  }

  /**
   * 카카오 2단계 — 신규 회원 추가정보 가입. 소셜 토큰으로 카카오 정보를 복원하고 약관·본인인증·주소·닉네임을 한 트랜잭션으로 저장한다 (비밀번호 없음 — 소셜 전용
   * 계정). 카카오 이메일이 기존 일반가입 계정과 충돌하면 거부한다(도용 방지). 소셜 토큰·본인인증 토큰 소비는 입력 검증을 모두 통과한 뒤 수행한다.
   */
  @Transactional
  public IssuedTokens signupSocial(SocialSignupRequest request) {
    OAuthUserInfo userInfo = socialAuthStore.require(request.socialToken());
    rejectIfKakaoEmailTaken(userInfo.email());
    validateNickname(request.nickname());
    if (request.address() == null) {
      throw new BusinessException(AuthErrorCode.DEFAULT_ADDRESS_REQUIRED);
    }

    String verifiedPhone = consumePhoneVerification(request.verificationToken(), request.phone());
    socialAuthStore.delete(request.socialToken());

    Customer customer =
        customerRepository.save(
            Customer.builder()
                .email(userInfo.email())
                .nickname(request.nickname())
                .phone(verifiedPhone)
                .phoneVerifiedAt(LocalDateTime.now())
                .build());
    customerOAuthAccountRepository.save(
        CustomerOAuthAccount.builder()
            .customer(customer)
            .provider(OAuthProviderType.KAKAO)
            .providerUserId(userInfo.providerUserId())
            .build());
    termService.recordAgreements(customer, request.agreedTermIds());
    addressService.create(customer.getId(), request.address());
    couponService.grantSignupCoupon(customer);

    log.info("카카오 신규 회원 가입 완료. customerId={}", customer.getId());
    return refreshTokenService.issueTokens(customer.getId(), Role.CUSTOMER);
  }

  /** 쿠키 refresh 로 새 access 만 재발급 (rotation X). 검증 실패 시 REFRESH_INVALID. */
  public TokenResponse refresh(String rawRefreshToken) {
    return refreshTokenService.reissueAccess(rawRefreshToken);
  }

  /** 로그아웃 — refresh 세션(Redis) 무효화. 쿠키 토큰이 곧 자격증명이므로 별도 소유자 검증 불필요. */
  public void logout(String rawRefreshToken) {
    refreshTokenService.revoke(rawRefreshToken);
  }

  @Transactional
  public PasswordResetVerifyResponse verifyCustomerPasswordResetIdentity(
      PasswordResetVerifyRequest request) {
    Customer customer =
        customerRepository
            .findByEmail(request.email())
            .filter(c -> !c.isDeleted())
            .orElseThrow(() -> new BusinessException(AuthErrorCode.RESET_VERIFICATION_FAILED));
    if (customer.getPasswordHash() == null) {
      throw new BusinessException(AuthErrorCode.SOCIAL_ONLY_ACCOUNT);
    }
    String verifiedPhone = consumePasswordResetVerification(request);
    if (!verifiedPhone.equals(customer.getPhone())) {
      throw new BusinessException(AuthErrorCode.RESET_VERIFICATION_FAILED);
    }
    return new PasswordResetVerifyResponse(
        passwordResetStore.issueToken(Role.CUSTOMER, customer.getId()));
  }

  @Transactional
  public PasswordResetVerifyResponse verifySellerPasswordResetIdentity(
      PasswordResetVerifyRequest request) {
    Seller seller =
        sellerRepository
            .findByEmail(request.email())
            .filter(s -> !s.isDeleted())
            .orElseThrow(() -> new BusinessException(AuthErrorCode.RESET_VERIFICATION_FAILED));
    String verifiedPhone = consumePasswordResetVerification(request);
    if (!verifiedPhone.equals(seller.getPhone())) {
      throw new BusinessException(AuthErrorCode.RESET_VERIFICATION_FAILED);
    }
    return new PasswordResetVerifyResponse(
        passwordResetStore.issueToken(Role.SELLER, seller.getId()));
  }

  @Transactional
  public void resetPassword(PasswordResetConfirmRequest request) {
    passwordValidator.validate(request.newPassword());
    PasswordResetStore.Subject subject = passwordResetStore.consume(request.resetToken());
    if (subject.role() == Role.SELLER) {
      Seller seller = findActiveSeller(subject.userId());
      seller.changePasswordHash(passwordEncoder.encode(request.newPassword()));
    } else {
      Customer customer = findActiveCustomer(subject.userId());
      if (customer.getPasswordHash() == null) {
        throw new BusinessException(AuthErrorCode.SOCIAL_ONLY_ACCOUNT);
      }
      customer.changePasswordHash(passwordEncoder.encode(request.newPassword()));
    }
    refreshTokenService.revokeAll(subject.role(), subject.userId());
  }

  @Transactional
  public void changePassword(
      Role role, Long userId, String currentRefreshToken, PasswordChangeRequest request) {
    passwordValidator.validate(request.newPassword());
    if (role == Role.SELLER) {
      Seller seller = findActiveSeller(userId);
      changePasswordHash(seller.getPasswordHash(), request, seller::changePasswordHash);
    } else {
      Customer customer = findActiveCustomer(userId);
      if (customer.getPasswordHash() == null) {
        throw new BusinessException(AuthErrorCode.SOCIAL_ONLY_ACCOUNT);
      }
      changePasswordHash(customer.getPasswordHash(), request, customer::changePasswordHash);
    }
    refreshTokenService.revokeOtherSessions(currentRefreshToken);
  }

  private KakaoLoginResult existingLogin(Customer customer) {
    log.info("소비자 카카오 로그인 성공(기존 회원). customerId={}", customer.getId());
    return new KakaoLoginResult.Existing(
        refreshTokenService.issueTokens(customer.getId(), Role.CUSTOMER));
  }

  /** 신규 카카오 회원 — 이메일 충돌 검사 후 소셜 토큰 발급. 실제 가입은 {@link #signupSocial} 에서. */
  private KakaoLoginResult newSignupPending(OAuthUserInfo userInfo) {
    rejectIfKakaoEmailTaken(userInfo.email());
    String socialToken = socialAuthStore.issueToken(userInfo);
    log.info("카카오 신규 회원 — 추가정보 가입 대기.");
    return new KakaoLoginResult.New(socialToken, userInfo.email(), userInfo.nickname());
  }

  /** 카카오 이메일이 기존 일반가입(비삭제) 계정과 충돌하면 거부 (자동 연결 안 함 — 도용 방지). */
  private void rejectIfKakaoEmailTaken(String email) {
    boolean taken = customerRepository.findByEmail(email).filter(c -> !c.isDeleted()).isPresent();
    if (taken) {
      throw new BusinessException(AuthErrorCode.EMAIL_ALREADY_REGISTERED);
    }
  }

  private void validateNickname(String nickname) {
    int length = nickname == null ? 0 : nickname.trim().length();
    if (length < NICKNAME_MIN || length > NICKNAME_MAX) {
      throw new BusinessException(CustomerErrorCode.NICKNAME_LENGTH);
    }
  }

  private void validateSellerName(String ownerName) {
    int length = ownerName == null ? 0 : ownerName.trim().length();
    if (length < SELLER_NAME_MIN || length > SELLER_NAME_MAX) {
      throw new BusinessException(SellerErrorCode.SELLER_NAME_INVALID);
    }
  }

  /** 본인인증 토큰 소비(1회용). 토큰 없음/만료/번호 불일치는 가입 레벨 PHONE_VERIFICATION_REQUIRED 로 통일한다. */
  private String consumePhoneVerification(String verificationToken, String phone) {
    try {
      return phoneVerificationService.consumeVerificationToken(verificationToken, phone);
    } catch (BusinessException e) {
      throw new BusinessException(AuthErrorCode.PHONE_VERIFICATION_REQUIRED);
    }
  }

  private String consumePasswordResetVerification(PasswordResetVerifyRequest request) {
    try {
      return phoneVerificationService.consumeVerificationToken(
          request.verificationToken(), request.phone());
    } catch (BusinessException e) {
      throw new BusinessException(AuthErrorCode.RESET_VERIFICATION_FAILED);
    }
  }

  private Customer findActiveCustomer(Long customerId) {
    return customerRepository
        .findById(customerId)
        .filter(c -> !c.isDeleted())
        .orElseThrow(() -> new BusinessException(AuthErrorCode.RESET_VERIFICATION_FAILED));
  }

  private Seller findActiveSeller(Long sellerId) {
    return sellerRepository
        .findById(sellerId)
        .filter(s -> !s.isDeleted())
        .orElseThrow(() -> new BusinessException(AuthErrorCode.RESET_VERIFICATION_FAILED));
  }

  private void changePasswordHash(
      String currentPasswordHash,
      PasswordChangeRequest request,
      java.util.function.Consumer<String> passwordChanger) {
    if (!passwordEncoder.matches(request.currentPassword(), currentPasswordHash)) {
      throw new BusinessException(AuthErrorCode.CURRENT_PASSWORD_MISMATCH);
    }
    passwordChanger.accept(passwordEncoder.encode(request.newPassword()));
  }

  private BusinessException invalidCredentials() {
    return new BusinessException(AuthErrorCode.LOGIN_FAILED);
  }
}
