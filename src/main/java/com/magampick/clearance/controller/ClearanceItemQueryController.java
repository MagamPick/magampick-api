package com.magampick.clearance.controller;

import com.magampick.clearance.dto.DealProductDetailResponse;
import com.magampick.clearance.service.ClearanceItemDetailQueryService;
import com.magampick.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 소비자 떨이 상품 조회 API. 기존 사장 전용 {@link ClearanceItemController} 와 URL 충돌 없음. */
@RestController
@RequestMapping("/api/v1/clearance-items")
@RequiredArgsConstructor
@Tag(name = "ClearanceItem (Consumer)", description = "소비자 떨이 상품 조회 API")
public class ClearanceItemQueryController {

  private final ClearanceItemDetailQueryService clearanceItemDetailQueryService;

  @GetMapping("/{clearanceItemId}")
  @Operation(
      summary = "떨이 상품 상세 조회",
      description =
          "떨이 상품 단건 상세 정보. 매장 미리보기(거리·영업상태·closingTime) + 평점 + dealStatus 포함. ROLE_CUSTOMER 인증 필요.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "조회 성공"),
    @ApiResponse(responseCode = "400", description = "기본 주소지 없음 (DEFAULT_ADDRESS_REQUIRED)"),
    @ApiResponse(responseCode = "401", description = "미인증"),
    @ApiResponse(responseCode = "403", description = "권한 없음 (ROLE_CUSTOMER 아님)"),
    @ApiResponse(responseCode = "404", description = "떨이 없음 (CLEARANCE_ITEM_NOT_FOUND)")
  })
  public DealProductDetailResponse detail(
      @PathVariable Long clearanceItemId, @AuthenticationPrincipal CustomUserDetails userDetails) {
    return clearanceItemDetailQueryService.getDetail(clearanceItemId, userDetails.getUserId());
  }
}
