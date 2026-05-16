package com.magampick.customer.controller;

import com.magampick.customer.dto.CustomerPhoneUpdateRequest;
import com.magampick.customer.dto.CustomerProfileResponse;
import com.magampick.customer.dto.CustomerProfileUpdateRequest;
import com.magampick.customer.service.CustomerService;
import com.magampick.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
@Tag(name = "Customer Profile", description = "소비자 본인 프로필 관리 API")
public class CustomerController {

  private final CustomerService customerService;

  @GetMapping("/me")
  @Operation(summary = "소비자 본인 프로필 조회", description = "JWT 의 customerId 에 해당하는 소비자의 프로필을 반환한다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "조회 성공"),
    @ApiResponse(responseCode = "404", description = "소비자 미존재 또는 탈퇴")
  })
  public CustomerProfileResponse getProfile(
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    return customerService.getProfile(userDetails.getUserId());
  }

  @PatchMapping("/me")
  @Operation(
      summary = "소비자 본인 닉네임 수정",
      description = "JWT 의 customerId 에 해당하는 소비자의 nickname 을 갱신한다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "수정 성공"),
    @ApiResponse(responseCode = "400", description = "입력 검증 실패"),
    @ApiResponse(responseCode = "404", description = "소비자 미존재 또는 탈퇴")
  })
  public CustomerProfileResponse updateProfile(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @Valid @RequestBody CustomerProfileUpdateRequest request) {
    return customerService.updateProfile(userDetails.getUserId(), request);
  }

  @PostMapping("/me/phone")
  @Operation(
      summary = "소비자 본인 휴대폰 변경",
      description = "본인인증 stub 을 통과한 새 휴대폰 번호로 갱신한다. phone_verified_at 도 함께 갱신.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "변경 성공"),
    @ApiResponse(responseCode = "400", description = "입력 검증 실패")
  })
  public CustomerProfileResponse updatePhone(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @Valid @RequestBody CustomerPhoneUpdateRequest request) {
    return customerService.updatePhone(userDetails.getUserId(), request);
  }
}
