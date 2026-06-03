package com.magampick.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.verify;
import static org.mockito.BDDMockito.willThrow;

import com.magampick.address.dto.AddressCreateRequest;
import com.magampick.address.service.AddressService;
import com.magampick.auth.dto.CustomerSignupRequest;
import com.magampick.auth.dto.IssuedTokens;
import com.magampick.auth.dto.KakaoLoginRequest;
import com.magampick.auth.dto.LoginRequest;
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
import com.magampick.global.security.Role;
import com.magampick.phone.exception.PhoneVerificationErrorCode;
import com.magampick.phone.service.PhoneVerificationService;
import com.magampick.seller.domain.Seller;
import com.magampick.seller.repository.SellerRepository;
import com.magampick.terms.exception.TermErrorCode;
import com.magampick.terms.service.TermService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

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

  @InjectMocks AuthService authService;

  private static final String RAW_PHONE = "010-1234-5678";
  private static final String VERIFIED_PHONE = "01012345678";

  private AddressCreateRequest validAddress() {
    return new AddressCreateRequest(
        "집", "서울특별시 강남구 테헤란로 427", null, "101동 1502호", "06158", 37.5066, 127.0535);
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
  void 사장_회원가입_사업자번호_중복도_허용() {
    SellerSignupRequest request =
        new SellerSignupRequest("seller@test.com", "Abcd1234!", "owner", "1234567890");
    Seller savedSeller =
        Seller.builder()
            .email(request.email())
            .passwordHash("encoded")
            .ownerName(request.ownerName())
            .businessNumber(request.businessNumber())
            .build();
    ReflectionTestUtils.setField(savedSeller, "id", 11L);

    given(sellerRepository.existsByEmail(request.email())).willReturn(false);
    given(passwordEncoder.encode(request.password())).willReturn("encoded");
    given(sellerRepository.save(any(Seller.class))).willReturn(savedSeller);
    given(refreshTokenService.issueTokens(11L, Role.SELLER))
        .willReturn(new IssuedTokens("access", "refresh", 1800L));

    IssuedTokens response = authService.signupSeller(request);

    assertThat(response.accessToken()).isEqualTo("access");
    verify(sellerRepository).save(any(Seller.class));
    verify(refreshTokenService).issueTokens(11L, Role.SELLER);
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
        .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.INVALID_CREDENTIALS);
  }

  @Test
  void 카카오_mock_로그인_신규_계정이면_소비자_생성() {
    KakaoLoginRequest request = new KakaoLoginRequest("mock-token");
    OAuthUserInfo userInfo = new OAuthUserInfo("kakao-uid", "kakao@test.com", "kakao_user");
    Customer customer =
        Customer.builder().email(userInfo.email()).nickname(userInfo.nickname()).build();
    ReflectionTestUtils.setField(customer, "id", 20L);

    given(kakaoOAuthProvider.getUserInfo(request.kakaoAccessToken())).willReturn(userInfo);
    given(
            customerOAuthAccountRepository.findByProviderAndProviderUserId(
                any(), eq(userInfo.providerUserId())))
        .willReturn(Optional.empty());
    given(customerRepository.findByEmail(userInfo.email())).willReturn(Optional.empty());
    given(customerRepository.save(any(Customer.class))).willReturn(customer);
    given(refreshTokenService.issueTokens(20L, Role.CUSTOMER))
        .willReturn(new IssuedTokens("access", "refresh", 1800L));

    IssuedTokens response = authService.kakaoLogin(request);

    assertThat(response.accessToken()).isEqualTo("access");
    verify(customerOAuthAccountRepository).save(any());
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
}
