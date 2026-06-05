package com.magampick.auth.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.magampick.address.dto.AddressCreateRequest;
import com.magampick.auth.dto.CustomerSignupRequest;
import com.magampick.auth.dto.IssuedTokens;
import com.magampick.auth.dto.KakaoLoginRequest;
import com.magampick.auth.dto.LoginRequest;
import com.magampick.auth.dto.SellerSignupRequest;
import com.magampick.auth.dto.SocialSignupRequest;
import com.magampick.auth.dto.TokenResponse;
import com.magampick.auth.exception.AuthErrorCode;
import com.magampick.auth.service.AuthService;
import com.magampick.auth.service.KakaoLoginResult;
import com.magampick.global.exception.BusinessException;
import com.magampick.global.security.CustomUserDetails;
import com.magampick.global.security.RefreshTokenCookie;
import com.magampick.global.security.Role;
import com.magampick.store.dto.StoreCreateRequest;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;

  @MockitoBean AuthService authService;
  @MockitoBean RefreshTokenCookie refreshTokenCookie;

  private static final ResponseCookie REFRESH_COOKIE =
      ResponseCookie.from("refresh_token", "r").httpOnly(true).path("/api/v1/auth").build();

  private CustomerSignupRequest validSignupRequest() {
    return new CustomerSignupRequest(
        "c@test.com",
        "Abcd1234!",
        "nick",
        "010-1234-5678",
        "vtoken",
        List.of(1L, 2L, 3L, 4L),
        new AddressCreateRequest(
            "집", "서울특별시 강남구 테헤란로 427", null, "101동 1502호", "06158", 37.5066, 127.0535));
  }

  private SocialSignupRequest validSocialSignupRequest() {
    return new SocialSignupRequest(
        "social-token",
        "nick",
        "010-1234-5678",
        "vtoken",
        List.of(1L, 2L, 3L, 4L),
        new AddressCreateRequest(
            "집", "서울특별시 강남구 테헤란로 427", null, "101동 1502호", "06158", 37.5066, 127.0535));
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
        "신선한 빵");
  }

  private SellerSignupRequest validSellerSignupRequest() {
    return new SellerSignupRequest(
        "s@test.com",
        "Abcd1234!",
        "홍길동",
        "010-1234-5678",
        "vtoken",
        List.of(1L, 2L, 3L, 6L),
        validStoreRequest());
  }

  private MockMultipartFile requestPart(Object request) throws Exception {
    return new MockMultipartFile(
        "request",
        "request",
        MediaType.APPLICATION_JSON_VALUE,
        objectMapper.writeValueAsBytes(request));
  }

  private MockMultipartFile imagePart() {
    return new MockMultipartFile("image", "store.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[1024]);
  }

  @Test
  void 소비자_회원가입_성공시_201_쿠키발급() throws Exception {
    given(authService.signupCustomer(any())).willReturn(new IssuedTokens("a", "r", 1800L));
    given(refreshTokenCookie.create(eq("r"), anyBoolean())).willReturn(REFRESH_COOKIE);

    mockMvc
        .perform(
            post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validSignupRequest())))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.accessToken").value("a"))
        .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refresh_token")))
        .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")));
  }

  @Test
  void 소비자_회원가입_검증_실패시_400() throws Exception {
    mockMvc
        .perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
  }

  @Test
  void 로그인_상태로_회원가입_진입시_403() throws Exception {
    CustomUserDetails principal = new CustomUserDetails(1L, Role.CUSTOMER);
    UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

    mockMvc
        .perform(
            post("/api/v1/auth/signup")
                .principal(auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validSignupRequest())))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
  }

  @Test
  void 사장_로그인_성공시_200_access바디_refresh쿠키() throws Exception {
    given(authService.loginSeller(any())).willReturn(new IssuedTokens("a", "r", 1800L));
    given(refreshTokenCookie.create(eq("r"), anyBoolean())).willReturn(REFRESH_COOKIE);

    mockMvc
        .perform(
            post("/api/v1/auth/seller/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new LoginRequest("s@test.com", "Abcd1234!", true))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.accessToken").value("a"))
        .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refresh_token")));
  }

  @Test
  void 카카오_기존회원_로그인시_200_EXISTING_쿠키발급() throws Exception {
    given(authService.kakaoLogin(any()))
        .willReturn(new KakaoLoginResult.Existing(new IssuedTokens("a", "r", 1800L)));
    given(refreshTokenCookie.create(eq("r"), anyBoolean())).willReturn(REFRESH_COOKIE);

    mockMvc
        .perform(
            post("/api/v1/auth/kakao")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new KakaoLoginRequest(
                            "auth-code", "https://app.example/login/kakao/callback"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("EXISTING"))
        .andExpect(jsonPath("$.data.accessToken").value("a"))
        .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refresh_token")));
  }

  @Test
  void 카카오_신규회원_분기시_200_NEW_소셜토큰() throws Exception {
    given(authService.kakaoLogin(any()))
        .willReturn(new KakaoLoginResult.New("social-token", "kakao@test.com", "카카오유저"));

    mockMvc
        .perform(
            post("/api/v1/auth/kakao")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new KakaoLoginRequest(
                            "auth-code", "https://app.example/login/kakao/callback"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("NEW"))
        .andExpect(jsonPath("$.data.socialToken").value("social-token"))
        .andExpect(jsonPath("$.data.email").value("kakao@test.com"));
  }

  @Test
  void 카카오_이메일_충돌시_409() throws Exception {
    given(authService.kakaoLogin(any()))
        .willThrow(new BusinessException(AuthErrorCode.EMAIL_ALREADY_REGISTERED));

    mockMvc
        .perform(
            post("/api/v1/auth/kakao")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new KakaoLoginRequest(
                            "auth-code", "https://app.example/login/kakao/callback"))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("EMAIL_ALREADY_REGISTERED"));
  }

  @Test
  void 소셜가입_성공시_201_쿠키발급() throws Exception {
    given(authService.signupSocial(any())).willReturn(new IssuedTokens("a", "r", 1800L));
    given(refreshTokenCookie.create(eq("r"), anyBoolean())).willReturn(REFRESH_COOKIE);

    mockMvc
        .perform(
            post("/api/v1/auth/signup/social")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validSocialSignupRequest())))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.accessToken").value("a"))
        .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refresh_token")));
  }

  @Test
  void 소셜가입_검증_실패시_400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/signup/social")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
  }

  @Test
  void 토큰_갱신_성공시_200() throws Exception {
    given(refreshTokenCookie.read(any())).willReturn(Optional.of("rawR"));
    given(authService.refresh("rawR")).willReturn(new TokenResponse("newA", 1800L));

    mockMvc
        .perform(post("/api/v1/auth/refresh").cookie(new Cookie("refresh_token", "rawR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.accessToken").value("newA"));
  }

  @Test
  void 토큰_갱신_refresh쿠키_없으면_401() throws Exception {
    given(refreshTokenCookie.read(any())).willReturn(Optional.empty());

    mockMvc
        .perform(post("/api/v1/auth/refresh"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("REFRESH_INVALID"));
  }

  @Test
  void 로그아웃_성공시_204_쿠키삭제() throws Exception {
    given(refreshTokenCookie.read(any())).willReturn(Optional.of("rawR"));
    given(refreshTokenCookie.clear())
        .willReturn(
            ResponseCookie.from("refresh_token", "").maxAge(0).path("/api/v1/auth").build());

    mockMvc
        .perform(post("/api/v1/auth/logout").cookie(new Cookie("refresh_token", "rawR")))
        .andExpect(status().isNoContent());
    verify(authService).logout("rawR");
  }

  @Test
  void 사장_회원가입_검증_실패시_400() throws Exception {
    mockMvc
        .perform(
            multipart("/api/v1/auth/seller/signup")
                .file(
                    new MockMultipartFile(
                        "request", "request", MediaType.APPLICATION_JSON_VALUE, "{}".getBytes()))
                .file(imagePart()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
  }

  @Test
  void 사장_회원가입_성공시_201() throws Exception {
    given(authService.signupSeller(any(), any())).willReturn(new IssuedTokens("a", "r", 1800L));
    given(refreshTokenCookie.create(eq("r"), anyBoolean())).willReturn(REFRESH_COOKIE);

    mockMvc
        .perform(
            multipart("/api/v1/auth/seller/signup")
                .file(requestPart(validSellerSignupRequest()))
                .file(imagePart()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.accessToken").value("a"));
  }

  @Test
  void 로그인_상태로_사장_회원가입_진입시_403() throws Exception {
    CustomUserDetails principal = new CustomUserDetails(1L, Role.SELLER);
    UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

    mockMvc
        .perform(
            multipart("/api/v1/auth/seller/signup")
                .file(requestPart(validSellerSignupRequest()))
                .file(imagePart())
                .principal(auth))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
  }
}
