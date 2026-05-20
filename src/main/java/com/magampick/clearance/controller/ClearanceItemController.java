package com.magampick.clearance.controller;

import com.magampick.clearance.dto.ClearanceItemCreateRequest;
import com.magampick.clearance.dto.ClearanceItemResponse;
import com.magampick.clearance.service.ClearanceItemService;
import com.magampick.global.response.PageResponse;
import com.magampick.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/seller/stores/{storeId}/clearance-items")
@RequiredArgsConstructor
@Tag(name = "ClearanceItem (Seller)", description = "사장 마감 임박 상품 관리 API")
public class ClearanceItemController {

  private final ClearanceItemService clearanceItemService;

  @PostMapping
  @Operation(
      summary = "마감 임박 상품 등록",
      description = "일반 상품을 마감 임박 상품으로 전환·등록한다. 매장이 APPROVED 상태여야 하며 원본 상품은 ON_SALE 이어야 한다.")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "등록 성공"),
    @ApiResponse(responseCode = "400", description = "입력 검증 실패 또는 픽업창·가격 규칙 위반"),
    @ApiResponse(responseCode = "401", description = "미인증"),
    @ApiResponse(responseCode = "403", description = "타인 매장 접근 또는 매장 미승인"),
    @ApiResponse(responseCode = "404", description = "원본 상품 없음"),
    @ApiResponse(responseCode = "409", description = "이미 진행 중인 마감 임박 상품 존재")
  })
  public ResponseEntity<ClearanceItemResponse> register(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @PathVariable Long storeId,
      @RequestBody @Valid ClearanceItemCreateRequest request) {
    ClearanceItemResponse response =
        clearanceItemService.registerClearanceItem(userDetails.getUserId(), storeId, request);
    return ResponseEntity.created(
            URI.create("/api/v1/seller/stores/" + storeId + "/clearance-items/" + response.id()))
        .body(response);
  }

  @GetMapping
  @Operation(summary = "본인 매장 마감 임박 상품 목록 조회", description = "본인 매장의 마감 임박 상품 목록을 페이지네이션으로 조회한다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "조회 성공"),
    @ApiResponse(responseCode = "401", description = "미인증"),
    @ApiResponse(responseCode = "403", description = "타인 매장 접근")
  })
  public PageResponse<ClearanceItemResponse> list(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @PathVariable Long storeId,
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return clearanceItemService.getMyClearanceItems(userDetails.getUserId(), storeId, pageable);
  }

  @GetMapping("/{clearanceItemId}")
  @Operation(summary = "본인 매장 마감 임박 상품 상세 조회")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "조회 성공"),
    @ApiResponse(responseCode = "401", description = "미인증"),
    @ApiResponse(responseCode = "403", description = "타인 매장 접근"),
    @ApiResponse(responseCode = "404", description = "마감 임박 상품 없음")
  })
  public ClearanceItemResponse detail(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @PathVariable Long storeId,
      @PathVariable Long clearanceItemId) {
    return clearanceItemService.getMyClearanceItem(
        userDetails.getUserId(), storeId, clearanceItemId);
  }
}
