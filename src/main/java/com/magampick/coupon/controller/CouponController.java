package com.magampick.coupon.controller;

import com.magampick.coupon.dto.CouponEventResponse;
import com.magampick.coupon.dto.CouponResponse;
import com.magampick.coupon.service.CouponService;
import com.magampick.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 소비자 쿠폰함 / 이벤트 쿠폰 API. */
@RestController
@RequestMapping("/api/v1/customers/me/coupons")
@RequiredArgsConstructor
@Tag(name = "Coupon (Customer)", description = "소비자 쿠폰함 / 이벤트 쿠폰 발급 API")
public class CouponController {

  private final CouponService couponService;

  @GetMapping
  @Operation(summary = "쿠폰함 조회")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "조회 성공"),
    @ApiResponse(responseCode = "401", description = "미인증"),
    @ApiResponse(responseCode = "403", description = "권한 없음")
  })
  public List<CouponResponse> getMyCoupons(@AuthenticationPrincipal CustomUserDetails userDetails) {
    return couponService.getMyCoupons(userDetails.getUserId());
  }

  @GetMapping("/events")
  @Operation(summary = "이벤트 쿠폰 목록 조회")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "조회 성공"),
    @ApiResponse(responseCode = "401", description = "미인증"),
    @ApiResponse(responseCode = "403", description = "권한 없음")
  })
  public List<CouponEventResponse> getEvents(
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    return couponService.getEvents(userDetails.getUserId());
  }

  @PostMapping("/events/{couponId}/claim")
  @Operation(summary = "이벤트 쿠폰 발급 (선착순)")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "발급 성공"),
    @ApiResponse(responseCode = "401", description = "미인증"),
    @ApiResponse(responseCode = "403", description = "권한 없음"),
    @ApiResponse(responseCode = "404", description = "쿠폰 없음"),
    @ApiResponse(responseCode = "409", description = "이미 받음 / 마감 / 발급 불가")
  })
  public ResponseEntity<CouponResponse> claim(
      @AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Long couponId) {
    CouponResponse response = couponService.claim(userDetails.getUserId(), couponId);
    return ResponseEntity.status(201).body(response);
  }
}
