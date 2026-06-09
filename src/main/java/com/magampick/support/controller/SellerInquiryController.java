package com.magampick.support.controller;

import com.magampick.global.security.CustomUserDetails;
import com.magampick.global.security.Role;
import com.magampick.support.dto.InquiryCreateRequest;
import com.magampick.support.dto.InquiryResponse;
import com.magampick.support.service.SupportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 사장 1:1 문의 API. /api/v1/seller/** — SecurityConfig 의 hasRole(SELLER) 로 보호됨. */
@RestController
@RequestMapping("/api/v1/seller/inquiries")
@RequiredArgsConstructor
@Tag(name = "Support (Seller)", description = "사장 1:1 문의 API — ROLE_SELLER 전용")
public class SellerInquiryController {

  private final SupportService supportService;

  @PostMapping
  @Operation(summary = "사장 문의 생성", description = "사장 1:1 문의 접수. 생성 시 상태는 PENDING.")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "생성 성공"),
    @ApiResponse(responseCode = "400", description = "입력 검증 실패"),
    @ApiResponse(responseCode = "401", description = "미인증"),
    @ApiResponse(responseCode = "403", description = "권한 없음")
  })
  public ResponseEntity<InquiryResponse> create(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @Valid @RequestBody InquiryCreateRequest request) {
    InquiryResponse response =
        supportService.createInquiry(Role.SELLER, userDetails.getUserId(), request);
    URI location = URI.create("/api/v1/seller/inquiries/" + response.id());
    return ResponseEntity.created(location).body(response);
  }

  @GetMapping
  @Operation(summary = "사장 내 문의 목록", description = "본인 문의 목록 최신순.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "조회 성공"),
    @ApiResponse(responseCode = "401", description = "미인증"),
    @ApiResponse(responseCode = "403", description = "권한 없음")
  })
  public List<InquiryResponse> list(@AuthenticationPrincipal CustomUserDetails userDetails) {
    return supportService.listMyInquiries(Role.SELLER, userDetails.getUserId());
  }

  @GetMapping("/{inquiryId}")
  @Operation(summary = "사장 내 문의 상세", description = "본인 문의 단건 조회. 타인 것이거나 없으면 404.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "조회 성공"),
    @ApiResponse(responseCode = "401", description = "미인증"),
    @ApiResponse(responseCode = "403", description = "권한 없음"),
    @ApiResponse(responseCode = "404", description = "문의 없음")
  })
  public InquiryResponse get(
      @AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Long inquiryId) {
    return supportService.getMyInquiry(Role.SELLER, userDetails.getUserId(), inquiryId);
  }
}
