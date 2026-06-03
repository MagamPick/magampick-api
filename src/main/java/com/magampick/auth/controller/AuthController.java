package com.magampick.auth.controller;

import com.magampick.auth.dto.CustomerSignupRequest;
import com.magampick.auth.dto.KakaoLoginRequest;
import com.magampick.auth.dto.LoginRequest;
import com.magampick.auth.dto.RefreshTokenRequest;
import com.magampick.auth.dto.SellerSignupRequest;
import com.magampick.auth.dto.TokenResponse;
import com.magampick.auth.service.AuthService;
import com.magampick.global.exception.BusinessException;
import com.magampick.global.exception.CommonErrorCode;
import com.magampick.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

  @PostMapping("/signup")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "소비자 회원가입", description = "소비자 계정을 생성하고 자동 로그인 토큰을 발급한다.")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "회원가입 성공"),
    @ApiResponse(responseCode = "400", description = "입력 검증 실패"),
    @ApiResponse(responseCode = "409", description = "이메일 중복")
  })
  public TokenResponse signupCustomer(
      Authentication authentication, @Valid @RequestBody CustomerSignupRequest request) {
    rejectIfAuthenticated(authentication);
    return authService.signupCustomer(request);
  }

  @PostMapping("/login")
  @Operation(summary = "소비자 로그인", description = "이메일/비밀번호로 소비자 로그인 후 토큰을 발급한다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "로그인 성공"),
    @ApiResponse(responseCode = "400", description = "입력 검증 실패"),
    @ApiResponse(responseCode = "401", description = "인증 실패")
  })
  public TokenResponse loginCustomer(@Valid @RequestBody LoginRequest request) {
    return authService.loginCustomer(request);
  }

  @PostMapping("/seller/signup")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "사장 회원가입", description = "사장 계정을 생성하고 자동 로그인 토큰을 발급한다.")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "회원가입 성공"),
    @ApiResponse(responseCode = "400", description = "입력 검증 실패"),
    @ApiResponse(responseCode = "409", description = "이메일 중복")
  })
  public TokenResponse signupSeller(@Valid @RequestBody SellerSignupRequest request) {
    return authService.signupSeller(request);
  }

  @PostMapping("/seller/login")
  @Operation(summary = "사장 로그인", description = "이메일/비밀번호로 사장 로그인 후 토큰을 발급한다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "로그인 성공"),
    @ApiResponse(responseCode = "400", description = "입력 검증 실패"),
    @ApiResponse(responseCode = "401", description = "인증 실패"),
    @ApiResponse(responseCode = "403", description = "미승인 사장")
  })
  public TokenResponse loginSeller(@Valid @RequestBody LoginRequest request) {
    return authService.loginSeller(request);
  }

  @PostMapping("/kakao")
  @Operation(summary = "카카오 로그인(Mock)", description = "카카오 OAuth Mock 사용자 정보로 소비자 로그인/자동가입을 처리한다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "로그인 성공"),
    @ApiResponse(responseCode = "400", description = "입력 검증 실패")
  })
  public TokenResponse kakaoLogin(@Valid @RequestBody KakaoLoginRequest request) {
    return authService.kakaoLogin(request);
  }

  @PostMapping("/refresh")
  @Operation(summary = "토큰 갱신", description = "refresh token rotation으로 access/refresh 토큰을 재발급한다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "갱신 성공"),
    @ApiResponse(responseCode = "401", description = "유효하지 않거나 만료된 토큰")
  })
  public TokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
    return authService.refresh(request);
  }

  @PostMapping("/logout")
  @Operation(summary = "로그아웃", description = "현재 기기의 refresh token을 무효화한다.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "로그아웃 성공"),
    @ApiResponse(responseCode = "401", description = "인증 실패")
  })
  public ResponseEntity<Void> logout(
      Authentication authentication, @Valid @RequestBody RefreshTokenRequest request) {
    if (authentication == null
        || !(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
      throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
    }
    authService.logout(userDetails.getUserId(), userDetails.getRole(), request);
    return ResponseEntity.noContent().build();
  }

  /** 비로그인 사용자만 회원가입 가능 — 이미 로그인된 토큰으로 진입하면 거부 (명세 "로그인 상태 진입 차단"). */
  private void rejectIfAuthenticated(Authentication authentication) {
    if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails) {
      throw new BusinessException(CommonErrorCode.FORBIDDEN);
    }
  }
}
