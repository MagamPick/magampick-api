package com.magampick.coupon.controller;

import com.magampick.coupon.dto.AdminCouponCreateRequest;
import com.magampick.coupon.dto.AdminCouponResponse;
import com.magampick.coupon.dto.AdminCouponUpdateRequest;
import com.magampick.coupon.service.CouponService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 쿠폰 이벤트 생성/관리 API. ROLE_ADMIN 전용. */
@RestController
@RequestMapping("/api/v1/admin/coupons")
@RequiredArgsConstructor
@Tag(name = "Coupon (Admin)", description = "관리자 이벤트 쿠폰 생성/관리 API")
public class AdminCouponController {

  private final CouponService couponService;

  @PostMapping
  @Operation(summary = "이벤트 쿠폰 생성", description = "관리자 전용. ROLE_ADMIN 인증 필요.")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "생성 성공"),
    @ApiResponse(responseCode = "400", description = "입력 검증 실패 또는 기간 오류"),
    @ApiResponse(responseCode = "401", description = "미인증"),
    @ApiResponse(responseCode = "403", description = "권한 없음 (ROLE_ADMIN 아님)")
  })
  public ResponseEntity<AdminCouponResponse> createEvent(
      @Valid @RequestBody AdminCouponCreateRequest request) {
    return ResponseEntity.status(201).body(couponService.createEvent(request));
  }

  @GetMapping
  @Operation(summary = "이벤트 쿠폰 목록 조회", description = "관리자 전용. 전체 이벤트 쿠폰(상태 포함) 생성 최신순.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "조회 성공"),
    @ApiResponse(responseCode = "401", description = "미인증"),
    @ApiResponse(responseCode = "403", description = "권한 없음 (ROLE_ADMIN 아님)")
  })
  public List<AdminCouponResponse> listEvents() {
    return couponService.listEvents();
  }

  @PatchMapping("/{couponId}")
  @Operation(
      summary = "이벤트 쿠폰 부분 수정",
      description = "관리자 전용. null 필드는 변경하지 않는다. 이미 발급된 쿠폰 소급 적용 X.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "수정 성공"),
    @ApiResponse(responseCode = "400", description = "할인율 범위 오류 또는 기간 역전"),
    @ApiResponse(responseCode = "401", description = "미인증"),
    @ApiResponse(responseCode = "403", description = "권한 없음 (ROLE_ADMIN 아님)"),
    @ApiResponse(responseCode = "404", description = "쿠폰 없음")
  })
  public AdminCouponResponse updateEvent(
      @PathVariable Long couponId, @Valid @RequestBody AdminCouponUpdateRequest request) {
    return couponService.updateEvent(couponId, request);
  }

  @PostMapping("/{couponId}/end")
  @Operation(summary = "이벤트 쿠폰 조기 종료", description = "관리자 전용. active=false 로 즉시 ENDED 처리.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "종료 성공"),
    @ApiResponse(responseCode = "401", description = "미인증"),
    @ApiResponse(responseCode = "403", description = "권한 없음 (ROLE_ADMIN 아님)"),
    @ApiResponse(responseCode = "404", description = "쿠폰 없음")
  })
  public AdminCouponResponse endEvent(@PathVariable Long couponId) {
    return couponService.endEvent(couponId);
  }
}
