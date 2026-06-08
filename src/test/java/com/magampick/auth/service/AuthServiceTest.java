package com.magampick.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.verify;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import com.magampick.address.dto.AddressCreateRequest;
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
import com.magampick.customer.domain.Customer;
import com.magampick.customer.exception.CustomerErrorCode;
import com.magampick.customer.repository.CustomerRepository;
import com.magampick.global.common.GeometryUtil;
import com.magampick.global.exception.BusinessException;
import com.magampick.global.security.Role;
import com.magampick.notification.service.CustomerNotificationSettingService;
import com.magampick.notification.service.SellerNotificationSettingService;
import com.magampick.phone.exception.PhoneVerificationErrorCode;
import com.magampick.phone.service.PhoneVerificationService;
import com.magampick.seller.domain.Seller;
import com.magampick.seller.exception.SellerErrorCode;
import com.magampick.seller.repository.SellerRepository;
import com.magampick.store.dto.StoreCreateRequest;
import com.magampick.store.exception.StoreErrorCode;
import com.magampick.store.service.PreparedStoreRegistration;
import com.magampick.store.service.StoreService;
import com.magampick.terms.exception.TermErrorCode;
import com.magampick.terms.service.TermService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @Mock AdminRepository adminRepository;
  @Mock CustomerRepository customerRepository;
  @Mock SellerRepository sellerRepository;
  @Mock CustomerOAuthAccountRepository customerOAuthAccountRepository;
  @Mock RefreshTokenService refreshTokenService;
  @Mock PasswordValidator passwordValidator;
  @Mock PasswordEncoder passwordEncoder;
  @Mock OAuthProvider kakaoOAuthProvider;
  @Mock PhoneVerificationService phoneVerificationService;
  @Mock TermService termService;
  @Mock AddressService addressService;
  @Mock SocialAuthStore socialAuthStore;
  @Mock PasswordResetStore passwordResetStore;
  @Mock StoreService storeService;
  @Mock TransactionTemplate transactionTemplate;
  @Mock CustomerNotificationSettingService customerNotificationSettingService;
  @Mock SellerNotificationSettingService sellerNotificationSettingService;

  @InjectMocks AuthService authService;

  private static final String RAW_PHONE = "010-1234-5678";
  private static final String VERIFIED_PHONE = "01012345678";

  private AddressCreateRequest validAddress() {
    return new AddressCreateRequest(
        "집", "서울특별시 강남구 테헤란로 427", null, "101동 1502호", "06158", "11680", "3179999");
  }

  private CustomerSignupRequest validRequest() {
    return new CustomerSignupRequest(
        "a@test.com",
        "Abcd1234!",
        "nick",
        RAW_PHONE,
        "vtoken",
        List.of(1L, 2L, 3L, 4L),
        validAddress());
  }

  private StoreCreateRequest validStoreRequest() {
    return new StoreCreateRequest(
        "123-45-67890",
        "홍길동",
        LocalDate.of(2024, 3, 15),
        "동네빵집",
        "서울 강남구 테헤란로 427",
        null,
        "1층",
        "06158",
        "0212345678",
        "신선한 빵",
        "11680",
        "3179999");
  }

  private SellerSignupRequest validSellerSignupRequest() {
    return new SellerSignupRequest(
        "seller@test.com",
        "Abcd1234!",
        "홍길동",
        RAW_PHONE,
        "vtoken",
        List.of(1L, 2L, 3L, 4L),
        validStoreRequest());
  }

  private MockMultipartFile validImage() {
    return new MockMultipartFile("image", "store.jpg", "image/jpeg", new byte[1024]);
  }

  private PreparedStoreRegistration preparedStoreRegistration(SellerSignupRequest request) {
    return new PreparedStoreRegistration(
        "1234567890",
        request.store().representativeName(),
        request.store().openDate(),
        request.store(),
        GeometryUtil.toPoint(37.5, 127.0),
        "/uploads/2026/6/store.jpg");
  }

  @Test
  void 이메일_가용성_조회_소비자_가능하면_available_TRUE() {
    // given
    given(customerRepository.existsByEmail("new@test.com")).willReturn(false);

    // when
    EmailAvailabilityResponse response =
        authService.checkEmailAvailability(Role.CUSTOMER, "new@test.com");

    // then
    assertThat(response.available()).isTrue();
  }

  @Test
  void 이메일_가용성_조회_사장_중복이면_예외() {
    // given
    given(sellerRepository.existsByEmail("seller@test.com")).willReturn(true);

    // when / then
    assertThatThrownBy(() -> authService.checkEmailAvailability(Role.SELLER, "seller@test.com"))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.EMAIL_ALREADY_EXISTS);
  }

  @Test
  void 이메일_가용성_조회_ADMIN_role_이면_INVALID_INPUT() {
    // when / then
    assertThatThrownBy(() -> authService.checkEmailAvailability(Role.ADMIN, "admin@test.com"))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue(
            "errorCode", com.magampick.global.exception.CommonErrorCode.INVALID_INPUT);
  }

  @SuppressWarnings("unchecked")
  private void stubTransactionTemplate() {
    given(transactionTemplate.execute(any()))
        .willAnswer(
            inv -> ((TransactionCallback<IssuedTokens>) inv.getArgument(0)).doInTransaction(null));
  }

  @Test
  void 소비자_회원가입_성공() {
    // given
    CustomerSignupRequest request = validRequest();
    Customer savedCustomer =
        Customer.builder()
            .email(request.email())
            .passwordHash("encoded")
            .nickname("nick")
            .phone(VERIFIED_PHONE)
            .phoneVerifiedAt(LocalDateTime.now())
            .build();
    ReflectionTestUtils.setField(savedCustomer, "id", 10L);
    IssuedTokens tokens = new IssuedTokens("access", "refresh", 1800L);

    given(customerRepository.existsByEmail(request.email())).willReturn(false);
    given(phoneVerificationService.consumeVerificationToken("vtoken", RAW_PHONE))
        .willReturn(VERIFIED_PHONE);
    given(passwordEncoder.encode(request.password())).willReturn("encoded");
    given(customerRepository.save(any(Customer.class))).willReturn(savedCustomer);
    given(refreshTokenService.issueTokens(10L, Role.CUSTOMER)).willReturn(tokens);

    // when
    IssuedTokens response = authService.signupCustomer(request);

    // then
    assertThat(response).isEqualTo(tokens);
    ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
    verify(customerRepository).save(captor.capture());
    assertThat(captor.getValue().getPhone()).isEqualTo(VERIFIED_PHONE);
    assertThat(captor.getValue().getPhoneVerifiedAt()).isNotNull();
    verify(termService).recordAgreements(savedCustomer, request.agreedTermIds());
    verify(addressService).create(10L, request.address());
  }

  @Test
  void 소비자_회원가입_이메일_중복시_예외() {
    CustomerSignupRequest request = validRequest();
    given(customerRepository.existsByEmail(request.email())).willReturn(true);

    assertThatThrownBy(() -> authService.signupCustomer(request))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.EMAIL_ALREADY_EXISTS);
  }

  @Test
  void 소비자_회원가입_비밀번호_정책_위반시_예외() {
    CustomerSignupRequest request = validRequest();
    given(customerRepository.existsByEmail(request.email())).willReturn(false);
    willThrow(new BusinessException(AuthErrorCode.PASSWORD_POLICY_VIOLATION))
        .given(passwordValidator)
        .validate(request.password());

    assertThatThrownBy(() -> authService.signupCustomer(request))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.PASSWORD_POLICY_VIOLATION);
  }

  @Test
  void 소비자_회원가입_닉네임_길이_위반시_예외() {
    CustomerSignupRequest request =
        new CustomerSignupRequest(
            "a@test.com", "Abcd1234!", "a", RAW_PHONE, "vtoken", List.of(1L), validAddress());
    given(customerRepository.existsByEmail(request.email())).willReturn(false);

    assertThatThrownBy(() -> authService.signupCustomer(request))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", CustomerErrorCode.NICKNAME_LENGTH);
  }

  @Test
  void 소비자_회원가입_기본주소_누락시_예외() {
    CustomerSignupRequest request =
        new CustomerSignupRequest(
            "a@test.com", "Abcd1234!", "nick", RAW_PHONE, "vtoken", List.of(1L), null);
    given(customerRepository.existsByEmail(request.email())).willReturn(false);

    assertThatThrownBy(() -> authService.signupCustomer(request))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.DEFAULT_ADDRESS_REQUIRED);
  }

  @Test
  void 소비자_회원가입_본인인증_토큰_무효시_예외() {
    CustomerSignupRequest request = validRequest();
    given(customerRepository.existsByEmail(request.email())).willReturn(false);
    willThrow(new BusinessException(PhoneVerificationErrorCode.PHONE_VERIFICATION_EXPIRED))
        .given(phoneVerificationService)
        .consumeVerificationToken("vtoken", RAW_PHONE);

    assertThatThrownBy(() -> authService.signupCustomer(request))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.PHONE_VERIFICATION_REQUIRED);
  }

  @Test
  void 소비자_회원가입_필수약관_미동의시_예외() {
    CustomerSignupRequest request = validRequest();
    Customer savedCustomer = Customer.builder().email(request.email()).nickname("nick").build();
    ReflectionTestUtils.setField(savedCustomer, "id", 10L);
    given(customerRepository.existsByEmail(request.email())).willReturn(false);
    given(phoneVerificationService.consumeVerificationToken("vtoken", RAW_PHONE))
        .willReturn(VERIFIED_PHONE);
    given(passwordEncoder.encode(request.password())).willReturn("encoded");
    given(customerRepository.save(any(Customer.class))).willReturn(savedCustomer);
    willThrow(new BusinessException(TermErrorCode.REQUIRED_TERMS_NOT_AGREED))
        .given(termService)
        .recordAgreements(savedCustomer, request.agreedTermIds());

    assertThatThrownBy(() -> authService.signupCustomer(request))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", TermErrorCode.REQUIRED_TERMS_NOT_AGREED);
  }

  @Test
  void 사장_회원가입_첫_매장까지_통합_성공() {
    SellerSignupRequest request = validSellerSignupRequest();
    PreparedStoreRegistration prepared = preparedStoreRegistration(request);
    Seller savedSeller =
        Seller.builder()
            .email(request.email())
            .passwordHash("encoded")
            .ownerName(request.ownerName())
            .phone(VERIFIED_PHONE)
            .phoneVerifiedAt(LocalDateTime.now())
            .build();
    ReflectionTestUtils.setField(savedSeller, "id", 11L);

    given(sellerRepository.existsByEmail(request.email())).willReturn(false);
    given(storeService.prepareStoreRegistration(eq(request.store()), any())).willReturn(prepared);
    given(phoneVerificationService.consumeVerificationToken("vtoken", RAW_PHONE))
        .willReturn(VERIFIED_PHONE);
    given(passwordEncoder.encode(request.password())).willReturn("encoded");
    given(sellerRepository.save(any(Seller.class))).willReturn(savedSeller);
    given(refreshTokenService.issueTokens(11L, Role.SELLER))
        .willReturn(new IssuedTokens("access", "refresh", 1800L));
    stubTransactionTemplate();

    IssuedTokens response = authService.signupSeller(request, validImage());

    assertThat(response.accessToken()).isEqualTo("access");
    ArgumentCaptor<Seller> captor = ArgumentCaptor.forClass(Seller.class);
    verify(sellerRepository).save(captor.capture());
    assertThat(captor.getValue().getPhone()).isEqualTo(VERIFIED_PHONE);
    verify(termService).recordSellerAgreements(savedSeller, request.agreedTermIds());
    verify(storeService).createStore(savedSeller, prepared);
    verify(refreshTokenService).issueTokens(11L, Role.SELLER);
  }

  @Test
  void 사장_회원가입_실명_길이_위반시_예외() {
    SellerSignupRequest request =
        new SellerSignupRequest(
            "seller@test.com",
            "Abcd1234!",
            "홍",
            RAW_PHONE,
            "vtoken",
            List.of(1L),
            validStoreRequest());
    given(sellerRepository.existsByEmail(request.email())).willReturn(false);

    assertThatThrownBy(() -> authService.signupSeller(request, validImage()))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", SellerErrorCode.SELLER_NAME_INVALID);
    verify(storeService, never()).prepareStoreRegistration(any(), any());
  }

  @Test
  void 사장_회원가입_매장_준비_실패시_seller_저장_없음() {
    SellerSignupRequest request = validSellerSignupRequest();
    given(sellerRepository.existsByEmail(request.email())).willReturn(false);
    willThrow(new BusinessException(StoreErrorCode.BUSINESS_INFO_MISMATCH))
        .given(storeService)
        .prepareStoreRegistration(eq(request.store()), any());

    assertThatThrownBy(() -> authService.signupSeller(request, validImage()))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", StoreErrorCode.BUSINESS_INFO_MISMATCH);
    verify(phoneVerificationService, never()).consumeVerificationToken(any(), any());
    verify(sellerRepository, never()).save(any());
  }

  @Test
  void 사장_회원가입_트랜잭션_실패시_업로드된_이미지_best_effort_삭제() {
    SellerSignupRequest request = validSellerSignupRequest();
    PreparedStoreRegistration prepared = preparedStoreRegistration(request);
    Seller savedSeller =
        Seller.builder()
            .email(request.email())
            .passwordHash("encoded")
            .ownerName(request.ownerName())
            .build();

    given(sellerRepository.existsByEmail(request.email())).willReturn(false);
    given(storeService.prepareStoreRegistration(eq(request.store()), any())).willReturn(prepared);
    given(phoneVerificationService.consumeVerificationToken("vtoken", RAW_PHONE))
        .willReturn(VERIFIED_PHONE);
    given(passwordEncoder.encode(request.password())).willReturn("encoded");
    given(sellerRepository.save(any(Seller.class))).willReturn(savedSeller);
    willThrow(new BusinessException(TermErrorCode.REQUIRED_TERMS_NOT_AGREED))
        .given(termService)
        .recordSellerAgreements(savedSeller, request.agreedTermIds());
    stubTransactionTemplate();

    assertThatThrownBy(() -> authService.signupSeller(request, validImage()))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", TermErrorCode.REQUIRED_TERMS_NOT_AGREED);
    verify(storeService).deletePreparedImageBestEffort(prepared);
  }

  @Test
  void 관리자_로그인_성공() {
    AdminLoginRequest request = new AdminLoginRequest("admin", "Admin1234!");
    Admin admin = Admin.builder().username("admin").passwordHash("encoded").name("관리자").build();
    given(adminRepository.findByUsername("admin")).willReturn(Optional.of(admin));
    given(passwordEncoder.matches("Admin1234!", "encoded")).willReturn(true);
    given(refreshTokenService.issueTokens(any(), eq(Role.ADMIN)))
        .willReturn(new IssuedTokens("access", "refresh", 1800L));

    IssuedTokens result = authService.loginAdmin(request);

    assertThat(result.accessToken()).isEqualTo("access");
  }

  @Test
  void 관리자_로그인_존재하지_않는_사용자명_예외() {
    AdminLoginRequest request = new AdminLoginRequest("nobody", "Admin1234!");
    given(adminRepository.findByUsername("nobody")).willReturn(Optional.empty());

    assertThatThrownBy(() -> authService.loginAdmin(request))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.LOGIN_FAILED);
  }

  @Test
  void 관리자_로그인_비밀번호_불일치_예외() {
    AdminLoginRequest request = new AdminLoginRequest("admin", "wrong!");
    Admin admin = Admin.builder().username("admin").passwordHash("encoded").name("관리자").build();
    given(adminRepository.findByUsername("admin")).willReturn(Optional.of(admin));
    given(passwordEncoder.matches("wrong!", "encoded")).willReturn(false);

    assertThatThrownBy(() -> authService.loginAdmin(request))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.LOGIN_FAILED);
  }

  @Test
  void 소비자_로그인_비밀번호_불일치시_예외() {
    LoginRequest request = new LoginRequest("customer@test.com", "wrong!", true);
    Customer customer =
        Customer.builder().email("customer@test.com").passwordHash("encoded").nickname("c").build();
    given(customerRepository.findByEmail(request.email())).willReturn(Optional.of(customer));
    given(passwordEncoder.matches(request.password(), customer.getPasswordHash()))
        .willReturn(false);

    assertThatThrownBy(() -> authService.loginCustomer(request))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.LOGIN_FAILED);
  }

  @Test
  void 카카오_신규회원이면_소셜토큰_발급하고_가입_보류() {
    // given — 카카오 매핑 없음 + 이메일 충돌 없음 → 소셜 토큰만 발급(가입은 /signup/social)
    KakaoLoginRequest request =
        new KakaoLoginRequest("auth-code", "https://app.example/login/kakao/callback");
    OAuthUserInfo userInfo = new OAuthUserInfo("kakao-uid", "kakao@test.com", "kakao_user");

    given(kakaoOAuthProvider.fetchUserInfo(request.authorizationCode(), request.redirectUri()))
        .willReturn(userInfo);
    given(
            customerOAuthAccountRepository.findByProviderAndProviderUserId(
                any(), eq(userInfo.providerUserId())))
        .willReturn(Optional.empty());
    given(customerRepository.findByEmail(userInfo.email())).willReturn(Optional.empty());
    given(socialAuthStore.issueToken(userInfo)).willReturn("social-token");

    // when
    KakaoLoginResult result = authService.kakaoLogin(request);

    // then — New + 소셜 토큰, customer/매핑 저장 안 함
    assertThat(result).isInstanceOf(KakaoLoginResult.New.class);
    KakaoLoginResult.New newMember = (KakaoLoginResult.New) result;
    assertThat(newMember.socialToken()).isEqualTo("social-token");
    assertThat(newMember.email()).isEqualTo("kakao@test.com");
    verify(customerRepository, never()).save(any());
    verify(customerOAuthAccountRepository, never()).save(any());
  }

  @Test
  void 카카오_이메일이_기존_가입계정과_충돌시_거부() {
    // given — 카카오 매핑 없음 + 같은 이메일의 기존 일반가입 계정 존재 (자동연결 거부)
    KakaoLoginRequest request =
        new KakaoLoginRequest("auth-code", "https://app.example/login/kakao/callback");
    OAuthUserInfo userInfo = new OAuthUserInfo("kakao-uid", "kakao@test.com", "kakao_user");
    Customer existing =
        Customer.builder().email(userInfo.email()).passwordHash("encoded").nickname("기존").build();

    given(kakaoOAuthProvider.fetchUserInfo(request.authorizationCode(), request.redirectUri()))
        .willReturn(userInfo);
    given(
            customerOAuthAccountRepository.findByProviderAndProviderUserId(
                any(), eq(userInfo.providerUserId())))
        .willReturn(Optional.empty());
    given(customerRepository.findByEmail(userInfo.email())).willReturn(Optional.of(existing));

    // when & then — 거부 + 신규 생성/매핑 저장 안 함
    assertThatThrownBy(() -> authService.kakaoLogin(request))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.EMAIL_ALREADY_REGISTERED);
    verify(customerRepository, never()).save(any());
    verify(customerOAuthAccountRepository, never()).save(any());
  }

  @Test
  void 카카오_기존_매핑이면_바로_로그인() {
    // given — 카카오 ID 매핑 존재 → 추가정보/생성 없이 바로 토큰 발급
    KakaoLoginRequest request =
        new KakaoLoginRequest("auth-code", "https://app.example/login/kakao/callback");
    OAuthUserInfo userInfo = new OAuthUserInfo("kakao-uid", "kakao@test.com", "kakao_user");
    Customer mapped = Customer.builder().email(userInfo.email()).nickname("기존").build();
    ReflectionTestUtils.setField(mapped, "id", 30L);
    CustomerOAuthAccount account =
        CustomerOAuthAccount.builder()
            .customer(mapped)
            .provider(OAuthProviderType.KAKAO)
            .providerUserId(userInfo.providerUserId())
            .build();

    given(kakaoOAuthProvider.fetchUserInfo(request.authorizationCode(), request.redirectUri()))
        .willReturn(userInfo);
    given(
            customerOAuthAccountRepository.findByProviderAndProviderUserId(
                any(), eq(userInfo.providerUserId())))
        .willReturn(Optional.of(account));
    given(refreshTokenService.issueTokens(30L, Role.CUSTOMER))
        .willReturn(new IssuedTokens("access", "refresh", 1800L));

    // when
    KakaoLoginResult result = authService.kakaoLogin(request);

    // then — Existing + 토큰, 기존 매핑 경로는 생성/매핑 저장 안 함
    assertThat(result).isInstanceOf(KakaoLoginResult.Existing.class);
    assertThat(((KakaoLoginResult.Existing) result).tokens().accessToken()).isEqualTo("access");
    verify(customerRepository, never()).save(any());
    verify(customerOAuthAccountRepository, never()).save(any());
  }

  @Test
  void 카카오_OAuth_인증_실패시_SOCIAL_AUTH_FAILED_전파() {
    // given — provider 가 인가 코드 교환/조회 실패로 SOCIAL_AUTH_FAILED 를 던짐
    KakaoLoginRequest request =
        new KakaoLoginRequest("bad-code", "https://app.example/login/kakao/callback");
    willThrow(new BusinessException(AuthErrorCode.SOCIAL_AUTH_FAILED))
        .given(kakaoOAuthProvider)
        .fetchUserInfo(request.authorizationCode(), request.redirectUri());

    // when & then — 전파 + 신규 생성/매핑 저장 안 함
    assertThatThrownBy(() -> authService.kakaoLogin(request))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.SOCIAL_AUTH_FAILED);
    verify(customerRepository, never()).save(any());
    verify(customerOAuthAccountRepository, never()).save(any());
  }

  @Test
  void 소셜가입_성공시_customer_oauth_약관_주소_저장() {
    // given
    SocialSignupRequest request =
        new SocialSignupRequest(
            "social-token", "닉네임", RAW_PHONE, "vtoken", List.of(1L, 2L, 3L, 4L), validAddress());
    OAuthUserInfo userInfo = new OAuthUserInfo("kakao-uid", "kakao@test.com", "kakao_user");
    Customer saved = Customer.builder().email(userInfo.email()).nickname("닉네임").build();
    ReflectionTestUtils.setField(saved, "id", 40L);

    given(socialAuthStore.require("social-token")).willReturn(userInfo);
    given(customerRepository.findByEmail(userInfo.email())).willReturn(Optional.empty());
    given(phoneVerificationService.consumeVerificationToken("vtoken", RAW_PHONE))
        .willReturn(VERIFIED_PHONE);
    given(customerRepository.save(any(Customer.class))).willReturn(saved);
    given(refreshTokenService.issueTokens(40L, Role.CUSTOMER))
        .willReturn(new IssuedTokens("access", "refresh", 1800L));

    // when
    IssuedTokens response = authService.signupSocial(request);

    // then
    assertThat(response.accessToken()).isEqualTo("access");
    verify(socialAuthStore).delete("social-token");
    verify(customerOAuthAccountRepository).save(any());
    verify(termService).recordAgreements(saved, request.agreedTermIds());
    verify(addressService).create(40L, request.address());
  }

  @Test
  void 소셜가입_소셜토큰_만료시_SOCIAL_TOKEN_INVALID() {
    // given — 소셜 토큰 만료/무효
    SocialSignupRequest request =
        new SocialSignupRequest("expired", "닉네임", RAW_PHONE, "vtoken", List.of(1L), validAddress());
    willThrow(new BusinessException(AuthErrorCode.SOCIAL_TOKEN_INVALID))
        .given(socialAuthStore)
        .require("expired");

    // when & then
    assertThatThrownBy(() -> authService.signupSocial(request))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.SOCIAL_TOKEN_INVALID);
    verify(customerRepository, never()).save(any());
  }

  @Test
  void 소셜가입_이메일이_기존_계정과_충돌시_거부() {
    // given — 카카오 이메일이 기존 일반가입 계정과 충돌
    SocialSignupRequest request =
        new SocialSignupRequest(
            "social-token", "닉네임", RAW_PHONE, "vtoken", List.of(1L), validAddress());
    OAuthUserInfo userInfo = new OAuthUserInfo("kakao-uid", "kakao@test.com", "kakao_user");
    Customer existing =
        Customer.builder().email(userInfo.email()).passwordHash("enc").nickname("기존").build();

    given(socialAuthStore.require("social-token")).willReturn(userInfo);
    given(customerRepository.findByEmail(userInfo.email())).willReturn(Optional.of(existing));

    // when & then
    assertThatThrownBy(() -> authService.signupSocial(request))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.EMAIL_ALREADY_REGISTERED);
    verify(customerRepository, never()).save(any());
    verify(socialAuthStore, never()).delete(any());
  }

  @Test
  void 토큰_갱신_성공시_새_access_발급() {
    // given — rotation 없음: refreshTokenService.reissueAccess 위임
    given(refreshTokenService.reissueAccess("rawR"))
        .willReturn(new TokenResponse("newAccess", 1800L));

    // when
    TokenResponse response = authService.refresh("rawR");

    // then
    assertThat(response.accessToken()).isEqualTo("newAccess");
  }

  @Test
  void 로그아웃시_refresh_세션_무효화_위임() {
    // when
    authService.logout("rawR");

    // then
    verify(refreshTokenService).revoke("rawR");
  }

  @Test
  void 소비자_비밀번호_재설정_본인확인_성공시_resetToken_발급() {
    // given
    PasswordResetVerifyRequest request =
        new PasswordResetVerifyRequest("customer@test.com", RAW_PHONE, "vtoken");
    Customer customer =
        Customer.builder()
            .email(request.email())
            .passwordHash("encoded")
            .nickname("소비자")
            .phone(VERIFIED_PHONE)
            .build();
    ReflectionTestUtils.setField(customer, "id", 10L);
    given(customerRepository.findByEmail(request.email())).willReturn(Optional.of(customer));
    given(phoneVerificationService.consumeVerificationToken("vtoken", RAW_PHONE))
        .willReturn(VERIFIED_PHONE);
    given(passwordResetStore.issueToken(Role.CUSTOMER, 10L)).willReturn("reset-token");

    // when
    PasswordResetVerifyResponse response = authService.verifyCustomerPasswordResetIdentity(request);

    // then
    assertThat(response.resetToken()).isEqualTo("reset-token");
  }

  @Test
  void 사장_비밀번호_재설정_본인확인_성공시_resetToken_발급() {
    // given
    PasswordResetVerifyRequest request =
        new PasswordResetVerifyRequest("seller@test.com", RAW_PHONE, "vtoken");
    Seller seller =
        Seller.builder()
            .email(request.email())
            .passwordHash("encoded")
            .ownerName("홍길동")
            .phone(VERIFIED_PHONE)
            .build();
    ReflectionTestUtils.setField(seller, "id", 11L);
    given(sellerRepository.findByEmail(request.email())).willReturn(Optional.of(seller));
    given(phoneVerificationService.consumeVerificationToken("vtoken", RAW_PHONE))
        .willReturn(VERIFIED_PHONE);
    given(passwordResetStore.issueToken(Role.SELLER, 11L)).willReturn("seller-reset-token");

    // when
    PasswordResetVerifyResponse response = authService.verifySellerPasswordResetIdentity(request);

    // then
    assertThat(response.resetToken()).isEqualTo("seller-reset-token");
  }

  @Test
  void 비밀번호_재설정_본인확인_소셜전용_소비자면_예외() {
    // given
    PasswordResetVerifyRequest request =
        new PasswordResetVerifyRequest("social@test.com", RAW_PHONE, "vtoken");
    Customer customer =
        Customer.builder().email(request.email()).nickname("소셜").phone(VERIFIED_PHONE).build();
    given(customerRepository.findByEmail(request.email())).willReturn(Optional.of(customer));

    // when / then
    assertThatThrownBy(() -> authService.verifyCustomerPasswordResetIdentity(request))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.SOCIAL_ONLY_ACCOUNT);
    verify(phoneVerificationService, never()).consumeVerificationToken(any(), any());
  }

  @Test
  void 비밀번호_재설정_완료시_비밀번호_변경하고_모든_refresh_세션_폐기() {
    // given
    PasswordResetConfirmRequest request =
        new PasswordResetConfirmRequest("reset-token", "Newpass123!");
    Customer customer =
        Customer.builder().email("customer@test.com").passwordHash("old").nickname("소비자").build();
    ReflectionTestUtils.setField(customer, "id", 10L);
    given(passwordResetStore.consume("reset-token"))
        .willReturn(new PasswordResetStore.Subject(Role.CUSTOMER, 10L));
    given(customerRepository.findById(10L)).willReturn(Optional.of(customer));
    given(passwordEncoder.encode("Newpass123!")).willReturn("new-encoded");

    // when
    authService.resetPassword(request);

    // then
    assertThat(customer.getPasswordHash()).isEqualTo("new-encoded");
    verify(refreshTokenService).revokeAll(Role.CUSTOMER, 10L);
  }

  @Test
  void 사장_비밀번호_재설정_완료시_비밀번호_변경하고_모든_refresh_세션_폐기() {
    // given
    PasswordResetConfirmRequest request =
        new PasswordResetConfirmRequest("seller-reset-token", "Newpass123!");
    Seller seller =
        Seller.builder().email("seller@test.com").passwordHash("old").ownerName("홍길동").build();
    ReflectionTestUtils.setField(seller, "id", 11L);
    given(passwordResetStore.consume("seller-reset-token"))
        .willReturn(new PasswordResetStore.Subject(Role.SELLER, 11L));
    given(sellerRepository.findById(11L)).willReturn(Optional.of(seller));
    given(passwordEncoder.encode("Newpass123!")).willReturn("new-encoded");

    // when
    authService.resetPassword(request);

    // then
    assertThat(seller.getPasswordHash()).isEqualTo("new-encoded");
    verify(refreshTokenService).revokeAll(Role.SELLER, 11L);
  }

  @Test
  void 비밀번호_변경_성공시_현재_세션만_유지하고_나머지_refresh_폐기() {
    // given
    Customer customer =
        Customer.builder()
            .email("customer@test.com")
            .passwordHash("old-encoded")
            .nickname("소비자")
            .build();
    ReflectionTestUtils.setField(customer, "id", 10L);
    PasswordChangeRequest request = new PasswordChangeRequest("Oldpass123!", "Newpass123!");
    given(customerRepository.findById(10L)).willReturn(Optional.of(customer));
    given(passwordEncoder.matches("Oldpass123!", "old-encoded")).willReturn(true);
    given(passwordEncoder.encode("Newpass123!")).willReturn("new-encoded");

    // when
    authService.changePassword(Role.CUSTOMER, 10L, "rawR", request);

    // then
    assertThat(customer.getPasswordHash()).isEqualTo("new-encoded");
    verify(refreshTokenService).revokeOtherSessions("rawR");
  }

  @Test
  void 사장_비밀번호_변경_성공시_현재_세션만_유지하고_나머지_refresh_폐기() {
    // given
    Seller seller =
        Seller.builder()
            .email("seller@test.com")
            .passwordHash("old-encoded")
            .ownerName("홍길동")
            .build();
    ReflectionTestUtils.setField(seller, "id", 11L);
    PasswordChangeRequest request = new PasswordChangeRequest("Oldpass123!", "Newpass123!");
    given(sellerRepository.findById(11L)).willReturn(Optional.of(seller));
    given(passwordEncoder.matches("Oldpass123!", "old-encoded")).willReturn(true);
    given(passwordEncoder.encode("Newpass123!")).willReturn("new-encoded");

    // when
    authService.changePassword(Role.SELLER, 11L, "rawSellerR", request);

    // then
    assertThat(seller.getPasswordHash()).isEqualTo("new-encoded");
    verify(refreshTokenService).revokeOtherSessions("rawSellerR");
  }

  @Test
  void 비밀번호_변경_현재_비밀번호_불일치시_예외() {
    // given
    Customer customer =
        Customer.builder()
            .email("customer@test.com")
            .passwordHash("old-encoded")
            .nickname("소비자")
            .build();
    PasswordChangeRequest request = new PasswordChangeRequest("Wrongpass123!", "Newpass123!");
    given(customerRepository.findById(10L)).willReturn(Optional.of(customer));
    given(passwordEncoder.matches("Wrongpass123!", "old-encoded")).willReturn(false);

    // when / then
    assertThatThrownBy(() -> authService.changePassword(Role.CUSTOMER, 10L, "rawR", request))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.CURRENT_PASSWORD_MISMATCH);
    verify(refreshTokenService, never()).revokeOtherSessions(any());
  }
}
