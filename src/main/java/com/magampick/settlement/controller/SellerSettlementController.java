package com.magampick.settlement.controller;

import com.magampick.global.security.CustomUserDetails;
import com.magampick.settlement.dto.SettlementCycleResponse;
import com.magampick.settlement.dto.SettlementSummaryResponse;
import com.magampick.settlement.service.SettlementService;
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
import org.springframework.web.bind.annotation.RestController;

/** 사장 정산 조회 API. ROLE_SELLER 전용. */
@RestController
@RequiredArgsConstructor
@Tag(name = "Settlement (Seller)", description = "사장 정산 조회 API")
public class SellerSettlementController {

  private final SettlementService settlementService;

  @GetMapping("/api/v1/seller/stores/{storeId}/settlements")
  @Operation(
      summary = "정산 회차 목록",
      description = "사장 본인 매장의 정산 회차 목록. 최신순(year·month·half desc). ROLE_SELLER 인증 필요.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "조회 성공"),
    @ApiResponse(responseCode = "401", description = "미인증"),
    @ApiResponse(responseCode = "403", description = "권한 없음 또는 타인 매장"),
  })
  public ResponseEntity<List<SettlementCycleResponse>> listSettlements(
      @AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Long storeId) {
    return ResponseEntity.ok(settlementService.listSettlements(userDetails.getUserId(), storeId));
  }

  @GetMapping("/api/v1/seller/stores/{storeId}/settlements/summary")
  @Operation(
      summary = "정산 요약 카드",
      description = "가장 최근 SCHEDULED 정산 회차 요약. 없으면 204 No Content. ROLE_SELLER 인증 필요.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "조회 성공"),
    @ApiResponse(responseCode = "204", description = "예정 정산 없음"),
    @ApiResponse(responseCode = "401", description = "미인증"),
    @ApiResponse(responseCode = "403", description = "권한 없음 또는 타인 매장"),
  })
  public ResponseEntity<SettlementSummaryResponse> getSettlementSummary(
      @AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Long storeId) {
    return settlementService
        .getSettlementSummary(userDetails.getUserId(), storeId)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.noContent().build());
  }
}
