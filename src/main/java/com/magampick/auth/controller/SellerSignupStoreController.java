package com.magampick.auth.controller;

import com.magampick.store.dto.BusinessVerificationRequest;
import com.magampick.store.service.StoreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/seller/stores")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "회원가입/로그인/토큰 관리 API")
public class SellerSignupStoreController {

  private final StoreService storeService;

  @PostMapping("/business-verification")
  @Operation(
      summary = "사장 가입용 사업자 진위확인",
      description = "사장 회원가입 첫 매장 등록 단계에서 사업자 번호·대표자명·개업일자의 일치 여부를 확인한다.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "진위확인 통과"),
    @ApiResponse(
        responseCode = "400",
        description = "입력 검증 실패 / 사업자 번호 형식 오류 / 진위확인 불일치 / 정상 영업 아님"),
    @ApiResponse(responseCode = "503", description = "사업자 번호 검증 일시 실패 (재시도 안내)")
  })
  public ResponseEntity<Void> verifyBusiness(
      @RequestBody @Valid BusinessVerificationRequest request) {
    storeService.verifyBusiness(request);
    return ResponseEntity.noContent().build();
  }
}
