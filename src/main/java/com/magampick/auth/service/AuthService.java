package com.magampick.auth.service;

import com.magampick.address.service.AddressService;
import com.magampick.auth.domain.CustomerOAuthAccount;
import com.magampick.auth.domain.OAuthProviderType;
import com.magampick.auth.domain.RefreshToken;
import com.magampick.auth.dto.CustomerSignupRequest;
import com.magampick.auth.dto.KakaoLoginRequest;
import com.magampick.auth.dto.LoginRequest;
import com.magampick.auth.dto.RefreshTokenRequest;
import com.magampick.auth.dto.SellerSignupRequest;
import com.magampick.auth.dto.TokenResponse;
import com.magampick.auth.exception.AuthErrorCode;
import com.magampick.auth.oauth.OAuthProvider;
import com.magampick.auth.oauth.OAuthUserInfo;
import com.magampick.auth.repository.CustomerOAuthAccountRepository;
import com.magampick.customer.domain.Customer;
import com.magampick.customer.exception.CustomerErrorCode;
import com.magampick.customer.repository.CustomerRepository;
import com.magampick.global.exception.BusinessException;
import com.magampick.global.security.JwtProvider;
import com.magampick.global.security.Role;
import com.magampick.phone.service.PhoneVerificationService;
import com.magampick.seller.domain.Seller;
import com.magampick.seller.repository.SellerRepository;
import com.magampick.terms.service.TermService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

  private static final int NICKNAME_MIN = 2;
  private static final int NICKNAME_MAX = 12;

  private final CustomerRepository customerRepository;
  private final SellerRepository sellerRepository;
  private final CustomerOAuthAccountRepository customerOAuthAccountRepository;
  private final RefreshTokenService refreshTokenService;
  private final PasswordValidator passwordValidator;
  private final PasswordEncoder passwordEncoder;
  private final JwtProvider jwtProvider;
  private final OAuthProvider kakaoOAuthProvider;
  private final PhoneVerificationService phoneVerificationService;
  private final TermService termService;
  private final AddressService addressService;

  /**
   * 소비자 회원가입 오케스트레이션 (5단계 통합). 한 트랜잭션으로 본인인증 토큰 소비 → customers 생성 → 약관 동의 기록 → 기본 주소 생성 → 토큰 발급.
   * 본인인증 토큰 소비(Redis 삭제)는 입력 검증을 모두 통과한 뒤 수행한다 — Redis 는 JPA 롤백에 안 묶이므로, 이후 DB 실패 시 토큰이 소멸해 재인증이
   * 필요할 수 있다(드묾).
   */
  @Transactional
  public TokenResponse signupCustomer(CustomerSignupRequest request) {
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

    log.info("소비자 회원가입 완료. customerId={}", customer.getId());
    return refreshTokenService.issueTokens(customer.getId(), Role.CUSTOMER);
  }

  @Transactional
  public TokenResponse loginCustomer(LoginRequest request) {
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

  @Transactional
  public TokenResponse signupSeller(SellerSignupRequest request) {
    if (sellerRepository.existsByEmail(request.email())) {
      throw new BusinessException(AuthErrorCode.EMAIL_ALREADY_EXISTS);
    }
    passwordValidator.validate(request.password());

    Seller seller =
        sellerRepository.save(
            Seller.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .ownerName(request.ownerName())
                .businessNumber(request.businessNumber())
                .build());
    log.info("사장 회원가입 완료. sellerId={}", seller.getId());
    return refreshTokenService.issueTokens(seller.getId(), Role.SELLER);
  }

  @Transactional
  public TokenResponse loginSeller(LoginRequest request) {
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
  public TokenResponse kakaoLogin(KakaoLoginRequest request) {
    OAuthUserInfo userInfo = kakaoOAuthProvider.getUserInfo(request.kakaoAccessToken());

    Customer customer =
        customerOAuthAccountRepository
            .findByProviderAndProviderUserId(OAuthProviderType.KAKAO, userInfo.providerUserId())
            .map(CustomerOAuthAccount::getCustomer)
            .orElseGet(() -> upsertKakaoCustomer(userInfo));

    log.info("소비자 카카오 로그인 성공. customerId={}", customer.getId());
    return refreshTokenService.issueTokens(customer.getId(), Role.CUSTOMER);
  }

  @Transactional
  public TokenResponse refresh(RefreshTokenRequest request) {
    JwtProvider.TokenPayload payload = jwtProvider.parsePayload(request.refreshToken());
    RefreshToken refreshToken = refreshTokenService.getActiveByRawToken(request.refreshToken());
    if (refreshToken.isExpired(LocalDateTime.now())) {
      throw new BusinessException(
          com.magampick.global.security.exception.AuthErrorCode.INVALID_TOKEN);
    }
    if (!refreshToken.getOwnerId().equals(payload.userId())
        || refreshToken.getOwnerRole() != payload.role()) {
      throw new BusinessException(
          com.magampick.global.security.exception.AuthErrorCode.INVALID_TOKEN);
    }

    refreshTokenService.revoke(refreshToken);
    TokenResponse tokens = refreshTokenService.issueTokens(payload.userId(), payload.role());
    log.info("토큰 갱신됨. ownerId={}, ownerRole={}", payload.userId(), payload.role());
    return tokens;
  }

  @Transactional
  public void logout(Long userId, Role role, RefreshTokenRequest request) {
    RefreshToken refreshToken = refreshTokenService.getActiveByRawToken(request.refreshToken());
    if (!refreshToken.getOwnerId().equals(userId) || refreshToken.getOwnerRole() != role) {
      throw new BusinessException(
          com.magampick.global.security.exception.AuthErrorCode.INVALID_TOKEN);
    }
    refreshTokenService.revoke(refreshToken);
    log.info("로그아웃 완료. ownerId={}, ownerRole={}", userId, role);
  }

  private Customer upsertKakaoCustomer(OAuthUserInfo userInfo) {
    Customer customer =
        customerRepository
            .findByEmail(userInfo.email())
            .filter(existing -> !existing.isDeleted())
            .orElseGet(
                () ->
                    customerRepository.save(
                        Customer.builder()
                            .email(userInfo.email())
                            .nickname(userInfo.nickname())
                            .build()));

    CustomerOAuthAccount account =
        CustomerOAuthAccount.builder()
            .customer(customer)
            .provider(OAuthProviderType.KAKAO)
            .providerUserId(userInfo.providerUserId())
            .build();
    customerOAuthAccountRepository.save(account);
    return customer;
  }

  private void validateNickname(String nickname) {
    int length = nickname == null ? 0 : nickname.trim().length();
    if (length < NICKNAME_MIN || length > NICKNAME_MAX) {
      throw new BusinessException(CustomerErrorCode.NICKNAME_LENGTH);
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

  private BusinessException invalidCredentials() {
    return new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
  }
}
