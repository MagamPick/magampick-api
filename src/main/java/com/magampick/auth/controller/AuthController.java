package com.magampick.auth.controller;

import com.magampick.auth.dto.CustomerSignupRequest;
import com.magampick.auth.dto.IssuedTokens;
import com.magampick.auth.dto.KakaoAuthResponse;
import com.magampick.auth.dto.KakaoLoginRequest;
import com.magampick.auth.dto.LoginRequest;
import com.magampick.auth.dto.SellerSignupRequest;
import com.magampick.auth.dto.SocialSignupRequest;
import com.magampick.auth.dto.TokenResponse;
import com.magampick.auth.service.AuthService;
import com.magampick.auth.service.KakaoLoginResult;
import com.magampick.global.exception.BusinessException;
import com.magampick.global.exception.CommonErrorCode;
import com.magampick.global.security.CustomUserDetails;
import com.magampick.global.security.RefreshTokenCookie;
import com.magampick.global.security.exception.AuthErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "회원가입/로그인/토큰 관리 API")
public class AuthController {

  private final AuthService authService;
  private final RefreshTokenCookie refreshTokenCookie;

  @PostMapping("/signup")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "소비자 회원가입",
      description = "소비자 계정을 생성하고 자동 로그인한다. refresh 는 HttpOnly 쿠키로 발급.")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "회원가입 성공"),
    @ApiResponse(responseCode = "400", description = "입력 검증 실패"),
    @ApiResponse(responseCode = "403", description = "로그인 상태 진입"),
    @ApiResponse(responseCode = "409", description = "이메일 중복")
  })
  public TokenResponse signupCustomer(
      Authentication authentication,
      @Valid @RequestBody CustomerSignupRequest request,
      HttpServletResponse response) {
    rejectIfAuthenticated(authentication);
    return issue(authService.signupCustomer(request), true, response);
  }

  @PostMapping("/login")
  @Operation(summary = "소비자 로그인", description = "이메일/비밀번호 로그인. access 는 바디, refresh 는 HttpOnly 쿠키.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "로그인 성공"),
    @ApiResponse(responseCode = "400", description = "입력 검증 실패"),
    @ApiResponse(responseCode = "401", description = "인증 실패")
  })
  public TokenResponse loginCustomer(
      @Valid @RequestBody LoginRequest request, HttpServletResponse response) {
    return issue(authService.loginCustomer(request), request.persistent(), response);
  }

  @PostMapping("/seller/signup")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "사장 회원가입", description = "사장 계정을 생성하고 자동 로그인한다. refresh 는 HttpOnly 쿠키로 발급.")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "회원가입 성공"),
    @ApiResponse(responseCode = "400", description = "입력 검증 실패"),
    @ApiResponse(responseCode = "409", description = "이메일 중복")
  })
  public TokenResponse signupSeller(
      @Valid @RequestBody SellerSignupRequest request, HttpServletResponse response) {
    return issue(authService.signupSeller(request), true, response);
  }

  @PostMapping("/seller/login")
  @Operation(summary = "사장 로그인", description = "이메일/비밀번호 로그인. access 는 바디, refresh 는 HttpOnly 쿠키.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "로그인 성공"),
    @ApiResponse(responseCode = "400", description = "입력 검증 실패"),
    @ApiResponse(responseCode = "401", description = "인증 실패")
  })
  public TokenResponse loginSeller(
      @Valid @RequestBody LoginRequest request, HttpServletResponse response) {
    return issue(authService.loginSeller(request), request.persistent(), response);
  }

  @PostMapping("/kakao")
  @Operation(
      summary = "카카오 로그인",
      description = "카카오 인가코드로 기존/신규 분기. 기존=즉시 로그인(refresh 쿠키), 신규=소셜 토큰 반환(추가정보 가입 필요).")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "기존 회원 로그인 / 신규 회원 추가정보 가입 필요"),
    @ApiResponse(responseCode = "400", description = "입력 검증 실패 / 카카오 이메일 미동의"),
    @ApiResponse(responseCode = "409", description = "카카오 이메일이 기존 계정과 충돌"),
    @ApiResponse(responseCode = "502", description = "카카오 OAuth 인증 실패")
  })
  public KakaoAuthResponse kakaoLogin(
      @Valid @RequestBody KakaoLoginRequest request, HttpServletResponse response) {
    return switch (authService.kakaoLogin(request)) {
      case KakaoLoginResult.Existing existing -> {
        IssuedTokens tokens = existing.tokens();
        response.addHeader(
            HttpHeaders.SET_COOKIE,
            refreshTokenCookie.create(tokens.refreshToken(), true).toString());
        yield KakaoAuthResponse.existing(tokens.accessToken(), tokens.accessExpiresInSeconds());
      }
      case KakaoLoginResult.New newMember ->
          KakaoAuthResponse.signupRequired(
              newMember.socialToken(), newMember.email(), newMember.nickname());
    };
  }

  @PostMapping("/signup/social")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "카카오 신규 회원 추가정보 가입",
      description = "소셜 토큰 + 약관·본인인증·주소·닉네임으로 가입하고 자동 로그인한다. refresh 는 HttpOnly 쿠키.")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "가입 성공"),
    @ApiResponse(responseCode = "400", description = "입력 검증 실패 / 소셜 토큰 만료 / 본인인증 미완료"),
    @ApiResponse(responseCode = "403", description = "로그인 상태 진입"),
    @ApiResponse(responseCode = "409", description = "카카오 이메일이 기존 계정과 충돌")
  })
  public TokenResponse signupSocial(
      Authentication authentication,
      @Valid @RequestBody SocialSignupRequest request,
      HttpServletResponse response) {
    rejectIfAuthenticated(authentication);
    return issue(authService.signupSocial(request), true, response);
  }

  @PostMapping("/refresh")
  @Operation(summary = "토큰 갱신", description = "refresh 쿠키로 새 access 토큰을 재발급한다 (rotation 없음).")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "갱신 성공"),
    @ApiResponse(responseCode = "401", description = "refresh 쿠키 없음/만료/무효")
  })
  public TokenResponse refresh(HttpServletRequest request) {
    String refreshToken =
        refreshTokenCookie
            .read(request)
            .orElseThrow(() -> new BusinessException(AuthErrorCode.REFRESH_INVALID));
    return authService.refresh(refreshToken);
  }

  @PostMapping("/logout")
  @Operation(summary = "로그아웃", description = "refresh 세션을 무효화하고 쿠키를 만료시킨다.")
  @ApiResponses(@ApiResponse(responseCode = "204", description = "로그아웃 성공"))
  public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
    refreshTokenCookie.read(request).ifPresent(authService::logout);
    response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookie.clear().toString());
    return ResponseEntity.noContent().build();
  }

  /** access(바디) + refresh(HttpOnly 쿠키) 발급. persistent 면 max-age 쿠키, 아니면 세션 쿠키. */
  private TokenResponse issue(
      IssuedTokens tokens, boolean persistent, HttpServletResponse response) {
    response.addHeader(
        HttpHeaders.SET_COOKIE,
        refreshTokenCookie.create(tokens.refreshToken(), persistent).toString());
    return new TokenResponse(tokens.accessToken(), tokens.accessExpiresInSeconds());
  }

  /** 비로그인 사용자만 회원가입 가능 — 이미 로그인된 토큰으로 진입하면 거부 (명세 "로그인 상태 진입 차단"). */
  private void rejectIfAuthenticated(Authentication authentication) {
    if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails) {
      throw new BusinessException(CommonErrorCode.FORBIDDEN);
    }
  }
}
