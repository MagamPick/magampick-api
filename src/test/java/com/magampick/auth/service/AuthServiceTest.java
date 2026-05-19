package com.magampick.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.verify;

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
import com.magampick.customer.repository.CustomerRepository;
import com.magampick.global.exception.BusinessException;
import com.magampick.global.security.JwtProvider;
import com.magampick.global.security.Role;
import com.magampick.seller.domain.Seller;
import com.magampick.seller.repository.SellerRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
  @Mock JwtProvider jwtProvider;
  @Mock OAuthProvider kakaoOAuthProvider;

  @InjectMocks AuthService authService;

  @Test
  void 소비자_회원가입_성공() {
    // given
    CustomerSignupRequest request = new CustomerSignupRequest("a@test.com", "Abcd1234!", "nick");
    Customer savedCustomer =
        Customer.builder()
            .email(request.email())
            .passwordHash("encoded")
            .nickname(request.nickname())
            .build();
    ReflectionTestUtils.setField(savedCustomer, "id", 10L);
    TokenResponse tokens = new TokenResponse("access", "refresh", 1800L);

    given(customerRepository.existsByEmail(request.email())).willReturn(false);
    given(passwordEncoder.encode(request.password())).willReturn("encoded");
    given(customerRepository.save(any(Customer.class))).willReturn(savedCustomer);
    given(refreshTokenService.issueTokens(10L, Role.CUSTOMER)).willReturn(tokens);

    // when
    TokenResponse response = authService.signupCustomer(request);

    // then
    assertThat(response).isEqualTo(tokens);
    verify(passwordValidator).validate(request.password());
    verify(refreshTokenService).issueTokens(10L, Role.CUSTOMER);
  }

  @Test
  void 소비자_회원가입_이메일_중복시_예외() {
    // given
    CustomerSignupRequest request = new CustomerSignupRequest("dup@test.com", "Abcd1234!", "nick");
    given(customerRepository.existsByEmail(request.email())).willReturn(true);

    // when / then
    assertThatThrownBy(() -> authService.signupCustomer(request))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.EMAIL_ALREADY_EXISTS);
  }

  @Test
  void 사장_회원가입_사업자번호_중복도_허용() {
    // given
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
        .willReturn(new TokenResponse("access", "refresh", 1800L));

    // when
    TokenResponse response = authService.signupSeller(request);

    // then
    assertThat(response.accessToken()).isEqualTo("access");
    verify(sellerRepository).save(any(Seller.class));
    verify(refreshTokenService).issueTokens(11L, Role.SELLER);
  }

  @Test
  void 소비자_로그인_비밀번호_불일치시_예외() {
    // given
    LoginRequest request = new LoginRequest("customer@test.com", "wrong!");
    Customer customer =
        Customer.builder().email("customer@test.com").passwordHash("encoded").nickname("c").build();
    given(customerRepository.findByEmail(request.email())).willReturn(Optional.of(customer));
    given(passwordEncoder.matches(request.password(), customer.getPasswordHash()))
        .willReturn(false);

    // when / then
    assertThatThrownBy(() -> authService.loginCustomer(request))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.INVALID_CREDENTIALS);
  }

  @Test
  void 카카오_mock_로그인_신규_계정이면_소비자_생성() {
    // given
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
        .willReturn(new TokenResponse("access", "refresh", 1800L));

    // when
    TokenResponse response = authService.kakaoLogin(request);

    // then
    assertThat(response.accessToken()).isEqualTo("access");
    verify(customerOAuthAccountRepository).save(any());
  }

  @Test
  void 토큰_갱신_성공시_refresh_token_rotation() {
    // given
    RefreshTokenRequest request = new RefreshTokenRequest("refresh-token");
    JwtProvider.TokenPayload payload =
        new JwtProvider.TokenPayload(1L, Role.CUSTOMER, LocalDateTime.now().plusDays(1));
    RefreshToken refreshToken =
        RefreshToken.builder()
            .ownerId(1L)
            .ownerRole(Role.CUSTOMER)
            .tokenHash("hash")
            .expiresAt(LocalDateTime.now().plusHours(1))
            .build();
    TokenResponse tokens = new TokenResponse("new-access", "new-refresh", 1800L);

    given(jwtProvider.parsePayload(request.refreshToken())).willReturn(payload);
    given(refreshTokenService.getActiveByRawToken(request.refreshToken())).willReturn(refreshToken);
    given(refreshTokenService.issueTokens(1L, Role.CUSTOMER)).willReturn(tokens);

    // when
    TokenResponse response = authService.refresh(request);

    // then
    assertThat(response).isEqualTo(tokens);
    verify(refreshTokenService).revoke(refreshToken);
  }

  @Test
  void 로그아웃_다른_사용자의_refresh_token이면_예외() {
    // given
    RefreshTokenRequest request = new RefreshTokenRequest("refresh-token");
    RefreshToken refreshToken =
        RefreshToken.builder()
            .ownerId(2L)
            .ownerRole(Role.CUSTOMER)
            .tokenHash("hash")
            .expiresAt(LocalDateTime.now().plusHours(1))
            .build();
    given(refreshTokenService.getActiveByRawToken(request.refreshToken())).willReturn(refreshToken);

    // when / then
    assertThatThrownBy(() -> authService.logout(1L, Role.CUSTOMER, request))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue(
            "errorCode", com.magampick.global.security.exception.AuthErrorCode.INVALID_TOKEN);
  }
}
