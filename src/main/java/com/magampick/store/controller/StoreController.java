package com.magampick.store.controller;

import com.magampick.global.security.CustomUserDetails;
import com.magampick.store.dto.BusinessVerificationRequest;
import com.magampick.store.dto.OperationStatusResponse;
import com.magampick.store.dto.OperationStatusTransitionRequest;
import com.magampick.store.dto.StoreCreateRequest;
import com.magampick.store.dto.StoreDetailResponse;
import com.magampick.store.dto.StoreRegisterResponse;
import com.magampick.store.dto.StoreResponse;
import com.magampick.store.service.StoreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/seller/stores")
@RequiredArgsConstructor
@Tag(name = "Store (Seller)", description = "사장 매장 관리 API")
public class StoreController {

  private final StoreService storeService;

  @PostMapping("/business-verification")
  @Operation(
      summary = "사업자 진위확인",
      description = "사업자 번호·대표자명·개업일자 세 값의 일치 여부를 국세청 API(stub)로 확인한다. 매장 등록 폼의 [조회하기] 버튼 대응.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "진위확인 통과"),
    @ApiResponse(
        responseCode = "400",
        description = "입력 검증 실패 / 사업자 번호 형식 오류 / 진위확인 불일치 / 정상 영업 아님"),
    @ApiResponse(responseCode = "401", description = "미인증"),
    @ApiResponse(responseCode = "403", description = "권한 없음 (ROLE_SELLER 아님)"),
    @ApiResponse(responseCode = "503", description = "사업자 번호 검증 일시 실패 (재시도 안내)")
  })
  public ResponseEntity<Void> verifyBusiness(
      @RequestBody @Valid BusinessVerificationRequest request) {
    storeService.verifyBusiness(request);
    return ResponseEntity.noContent().build();
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(
      summary = "매장 등록",
      description =
          "사업자 진위확인(번호·대표자명·개업일자) + 주소 지오코딩 + 대표 사진 업로드 후 자동 승인으로 매장을 즉시 생성한다. operation_status 초기값은 CLOSED_TODAY.")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "등록 성공"),
    @ApiResponse(
        responseCode = "400",
        description = "입력 검증 실패 / 사업자 번호 형식 오류 / 진위확인 불일치 / 정상 영업 아님 / 지오코딩 실패 / 이미지 규격 위반"),
    @ApiResponse(responseCode = "401", description = "미인증"),
    @ApiResponse(responseCode = "403", description = "권한 없음 (ROLE_SELLER 아님)"),
    @ApiResponse(responseCode = "503", description = "사업자 번호 검증 일시 실패 (재시도 안내)")
  })
  public ResponseEntity<StoreRegisterResponse> register(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @RequestPart("request") @Valid StoreCreateRequest request,
      @RequestPart("image") MultipartFile image) {
    StoreRegisterResponse response =
        storeService.registerStore(userDetails.getUserId(), request, image);
    return ResponseEntity.created(URI.create("/api/v1/seller/stores/" + response.storeId()))
        .body(response);
  }

  @GetMapping
  @Operation(summary = "본인 매장 목록 조회", description = "본인 소유 매장 전체 목록을 조회한다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "조회 성공"),
    @ApiResponse(responseCode = "401", description = "미인증"),
    @ApiResponse(responseCode = "403", description = "권한 없음 (ROLE_SELLER 아님)")
  })
  public ResponseEntity<List<StoreResponse>> list(
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    return ResponseEntity.ok(storeService.getMyStores(userDetails.getUserId()));
  }

  @GetMapping("/{storeId}")
  @Operation(summary = "본인 매장 상세 조회", description = "본인 소유 매장 상세 정보를 조회한다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "조회 성공"),
    @ApiResponse(responseCode = "401", description = "미인증"),
    @ApiResponse(responseCode = "403", description = "권한 없음 또는 타인 매장 접근")
  })
  public ResponseEntity<StoreDetailResponse> detail(
      @AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Long storeId) {
    return ResponseEntity.ok(storeService.getMyStore(userDetails.getUserId(), storeId));
  }

  @GetMapping("/{storeId}/operation-status")
  @Operation(
      summary = "매장 영업 상태 조회",
      description = "본인 매장의 현재 영업 상태와 오늘 영업 요일 여부 / 오늘 마감 시각을 조회한다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "조회 성공"),
    @ApiResponse(responseCode = "401", description = "미인증"),
    @ApiResponse(responseCode = "403", description = "권한 없음 또는 타인 매장 접근")
  })
  public ResponseEntity<OperationStatusResponse> getOperationStatus(
      @AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Long storeId) {
    return ResponseEntity.ok(storeService.getOperationStatus(userDetails.getUserId(), storeId));
  }

  @PatchMapping("/{storeId}/operation-status")
  @Operation(
      summary = "매장 영업 상태 전환",
      description =
          "본인 매장의 영업 상태를 OPEN / BREAK / CLOSED_TODAY 중 하나로 전환한다. 노션 전이 그래프 위반 시 409, OPEN 진입 시 오늘 휴무면 409 (STORE_CLOSED_TODAY).")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "전환 성공"),
    @ApiResponse(responseCode = "400", description = "입력 검증 실패 (to 누락 등)"),
    @ApiResponse(responseCode = "401", description = "미인증"),
    @ApiResponse(responseCode = "403", description = "권한 없음 또는 타인 매장 접근"),
    @ApiResponse(
        responseCode = "409",
        description = "금지 전이 (자기 전이 / CLOSED_TODAY→BREAK) 또는 OPEN 진입 시 오늘이 영업 요일 아님")
  })
  public ResponseEntity<OperationStatusResponse> transitionOperationStatus(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @PathVariable Long storeId,
      @RequestBody @Valid OperationStatusTransitionRequest request) {
    return ResponseEntity.ok(
        storeService.transitionOperationStatus(userDetails.getUserId(), storeId, request.to()));
  }
}
