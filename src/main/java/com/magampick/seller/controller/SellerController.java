package com.magampick.seller.controller;

import com.magampick.global.security.CustomUserDetails;
import com.magampick.seller.dto.SellerPhoneUpdateRequest;
import com.magampick.seller.dto.SellerProfileResponse;
import com.magampick.seller.dto.SellerProfileUpdateRequest;
import com.magampick.seller.service.SellerService;
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
@RequestMapping("/api/v1/seller")
@RequiredArgsConstructor
@Tag(name = "Seller Profile", description = "사장 본인 프로필 관리 API")
public class SellerController {

  private final SellerService sellerService;

  @GetMapping("/me")
  @Operation(summary = "사장 본인 프로필 조회", description = "JWT 의 sellerId 에 해당하는 사장의 프로필을 반환한다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "조회 성공"),
    @ApiResponse(responseCode = "404", description = "사장 미존재 또는 탈퇴")
  })
  public SellerProfileResponse getProfile(@AuthenticationPrincipal CustomUserDetails userDetails) {
    return sellerService.getProfile(userDetails.getUserId());
  }

  @PatchMapping("/me")
  @Operation(summary = "사장 본인 이름 수정", description = "JWT 의 sellerId 에 해당하는 사장의 ownerName 을 갱신한다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "수정 성공"),
    @ApiResponse(responseCode = "400", description = "입력 검증 실패"),
    @ApiResponse(responseCode = "404", description = "사장 미존재 또는 탈퇴")
  })
  public SellerProfileResponse updateProfile(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @Valid @RequestBody SellerProfileUpdateRequest request) {
    return sellerService.updateProfile(userDetails.getUserId(), request);
  }

  @PostMapping("/me/phone")
  @Operation(
      summary = "사장 본인 휴대폰 변경",
      description = "본인인증 stub 을 통과한 새 휴대폰 번호로 갱신한다. phone_verified_at 도 함께 갱신.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "변경 성공"),
    @ApiResponse(responseCode = "400", description = "입력 검증 실패")
  })
  public SellerProfileResponse updatePhone(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @Valid @RequestBody SellerPhoneUpdateRequest request) {
    return sellerService.updatePhone(userDetails.getUserId(), request);
  }
}
