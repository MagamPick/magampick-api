package com.magampick.coupon.controller;

import com.magampick.coupon.dto.AdminCouponCreateRequest;
import com.magampick.coupon.dto.AdminCouponResponse;
import com.magampick.coupon.service.CouponService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 쿠폰 이벤트 생성 API. ROLE_ADMIN 전용. */
@RestController
@RequiredArgsConstructor
@Tag(name = "Coupon (Admin)", description = "관리자 이벤트 쿠폰 생성 API")
public class AdminCouponController {

  private final CouponService couponService;

  @PostMapping("/api/v1/admin/coupons")
  @Operation(summary = "이벤트 쿠폰 생성", description = "관리자 전용. ROLE_ADMIN 인증 필요.")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "생성 성공"),
    @ApiResponse(responseCode = "400", description = "입력 검증 실패"),
    @ApiResponse(responseCode = "401", description = "미인증"),
    @ApiResponse(responseCode = "403", description = "권한 없음 (ROLE_ADMIN 아님)")
  })
  public ResponseEntity<AdminCouponResponse> createEvent(
      @Valid @RequestBody AdminCouponCreateRequest request) {
    return ResponseEntity.status(201).body(couponService.createEvent(request));
  }
}
