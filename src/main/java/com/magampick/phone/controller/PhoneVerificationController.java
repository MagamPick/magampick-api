package com.magampick.phone.controller;

import com.magampick.phone.dto.PhoneVerificationConfirmRequest;
import com.magampick.phone.dto.PhoneVerificationRequest;
import com.magampick.phone.dto.PhoneVerificationTokenResponse;
import com.magampick.phone.service.PhoneVerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/phone-verifications")
@RequiredArgsConstructor
@Tag(name = "Phone Verification", description = "휴대폰 본인인증(SMS OTP) API")
public class PhoneVerificationController {

  private final PhoneVerificationService phoneVerificationService;

  @PostMapping
  @Operation(summary = "인증번호 발송", description = "휴대폰 번호로 6자리 SMS 인증번호를 발송한다. 회원가입·비밀번호 재설정에서 호출된다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "발송 성공"),
    @ApiResponse(responseCode = "400", description = "휴대폰 번호 형식 오류"),
    @ApiResponse(responseCode = "429", description = "재발송 제한 또는 일일 한도 초과"),
    @ApiResponse(responseCode = "502", description = "SMS 발송 실패")
  })
  public void request(@Valid @RequestBody PhoneVerificationRequest request) {
    phoneVerificationService.requestCode(request.phone());
  }

  @PostMapping("/confirm")
  @Operation(summary = "인증번호 검증", description = "인증번호를 검증하고 성공 시 본인인증 토큰(15분)을 발급한다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "검증 성공 — 본인인증 토큰 발급"),
    @ApiResponse(responseCode = "400", description = "인증번호 불일치/만료"),
    @ApiResponse(responseCode = "429", description = "검증 시도 횟수 초과")
  })
  public PhoneVerificationTokenResponse confirm(
      @Valid @RequestBody PhoneVerificationConfirmRequest request) {
    String token = phoneVerificationService.verifyCode(request.phone(), request.code());
    return new PhoneVerificationTokenResponse(token);
  }
}
