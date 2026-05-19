package com.magampick.store.controller;

import com.magampick.global.response.PageResponse;
import com.magampick.store.domain.StoreStatus;
import com.magampick.store.dto.StoreAdminDetailResponse;
import com.magampick.store.dto.StoreAdminResponse;
import com.magampick.store.dto.StoreRejectRequest;
import com.magampick.store.service.StoreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/stores")
@RequiredArgsConstructor
@Tag(name = "Store (Admin)", description = "관리자 매장 검토 API")
public class AdminStoreController {

  private final StoreService storeService;

  @GetMapping
  @Operation(
      summary = "매장 검토 큐 조회",
      description = "상태 필터(PENDING/APPROVED/REJECTED)로 매장 목록을 조회한다. status 생략 시 전체 조회.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "조회 성공"),
    @ApiResponse(responseCode = "401", description = "미인증"),
    @ApiResponse(responseCode = "403", description = "권한 없음 (ROLE_ADMIN 아님)")
  })
  public ResponseEntity<PageResponse<StoreAdminResponse>> list(
      @Parameter(description = "상태 필터 (PENDING / APPROVED / REJECTED)")
          @RequestParam(required = false)
          StoreStatus status,
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return ResponseEntity.ok(storeService.getStoresForAdmin(status, pageable));
  }

  @GetMapping("/{storeId}")
  @Operation(summary = "매장 상세 조회 (관리자)", description = "관리자용 매장 상세 정보를 조회한다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "조회 성공"),
    @ApiResponse(responseCode = "404", description = "매장 없음"),
    @ApiResponse(responseCode = "401", description = "미인증"),
    @ApiResponse(responseCode = "403", description = "권한 없음 (ROLE_ADMIN 아님)")
  })
  public ResponseEntity<StoreAdminDetailResponse> detail(@PathVariable Long storeId) {
    return ResponseEntity.ok(storeService.getStoreForAdmin(storeId));
  }

  @PatchMapping("/{storeId}/approve")
  @Operation(summary = "매장 등록 승인", description = "PENDING 상태의 매장을 승인한다.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "승인 성공"),
    @ApiResponse(responseCode = "404", description = "매장 없음"),
    @ApiResponse(responseCode = "409", description = "이미 심사가 완료된 매장"),
    @ApiResponse(responseCode = "401", description = "미인증"),
    @ApiResponse(responseCode = "403", description = "권한 없음 (ROLE_ADMIN 아님)")
  })
  public ResponseEntity<Void> approve(@PathVariable Long storeId) {
    storeService.approveStore(storeId);
    return ResponseEntity.noContent().build();
  }

  @PatchMapping("/{storeId}/reject")
  @Operation(summary = "매장 등록 반려", description = "PENDING 상태의 매장을 반려한다. 반려 사유 필수.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "반려 성공"),
    @ApiResponse(responseCode = "400", description = "반려 사유 미입력"),
    @ApiResponse(responseCode = "404", description = "매장 없음"),
    @ApiResponse(responseCode = "409", description = "이미 심사가 완료된 매장"),
    @ApiResponse(responseCode = "401", description = "미인증"),
    @ApiResponse(responseCode = "403", description = "권한 없음 (ROLE_ADMIN 아님)")
  })
  public ResponseEntity<Void> reject(
      @PathVariable Long storeId, @Valid @RequestBody StoreRejectRequest request) {
    storeService.rejectStore(storeId, request.rejectionReason());
    return ResponseEntity.noContent().build();
  }
}
