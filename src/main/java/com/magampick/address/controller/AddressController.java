package com.magampick.address.controller;

import com.magampick.address.dto.AddressCreateRequest;
import com.magampick.address.dto.AddressResponse;
import com.magampick.address.dto.AddressUpdateRequest;
import com.magampick.address.service.AddressService;
import com.magampick.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customers/me/addresses")
@RequiredArgsConstructor
@Tag(name = "Address", description = "소비자 주소지 관리 API")
public class AddressController {

  private final AddressService addressService;

  @PostMapping
  @Operation(
      summary = "주소지 등록",
      description = "본인 주소지를 등록한다. 최대 3개까지 보유 가능. 첫 등록 시 자동으로 기본 주소지로 지정된다.")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "등록 성공"),
    @ApiResponse(responseCode = "400", description = "검증 실패 또는 보유 한도 초과"),
    @ApiResponse(responseCode = "401", description = "미인증"),
    @ApiResponse(responseCode = "403", description = "권한 없음 (ROLE_CUSTOMER 아님)")
  })
  public ResponseEntity<AddressResponse> create(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @Valid @RequestBody AddressCreateRequest request) {
    AddressResponse response = addressService.create(userDetails.getUserId(), request);
    return ResponseEntity.created(URI.create("/api/v1/customers/me/addresses/" + response.id()))
        .body(response);
  }

  @GetMapping
  @Operation(summary = "주소지 목록 조회", description = "본인이 등록한 주소지 0~3개를 조회한다. 기본 주소지가 가장 위에 온다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "조회 성공"),
    @ApiResponse(responseCode = "401", description = "미인증"),
    @ApiResponse(responseCode = "403", description = "권한 없음 (ROLE_CUSTOMER 아님)")
  })
  public List<AddressResponse> list(@AuthenticationPrincipal CustomUserDetails userDetails) {
    return addressService.list(userDetails.getUserId());
  }

  @PatchMapping("/{addressId}")
  @Operation(
      summary = "주소지 수정",
      description = "본인 주소지의 라벨/주소/좌표를 부분 수정한다. 기본 주소지 변경은 별도 endpoint 사용.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "수정 성공"),
    @ApiResponse(responseCode = "400", description = "검증 실패"),
    @ApiResponse(responseCode = "401", description = "미인증"),
    @ApiResponse(responseCode = "403", description = "권한 없음 또는 본인 소유 아님"),
    @ApiResponse(responseCode = "404", description = "주소지를 찾을 수 없음")
  })
  public AddressResponse update(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @Parameter(description = "주소지 ID", example = "1") @PathVariable Long addressId,
      @Valid @RequestBody AddressUpdateRequest request) {
    return addressService.update(userDetails.getUserId(), addressId, request);
  }

  @PostMapping("/{addressId}/default")
  @Operation(
      summary = "기본 주소지 변경",
      description = "지정한 주소를 기본 주소지로 설정한다. 기존 기본 주소지는 자동으로 해제된다. 이미 기본 주소지인 경우 멱등하게 처리된다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "변경 성공 (또는 이미 기본 주소지인 멱등 응답)"),
    @ApiResponse(responseCode = "401", description = "미인증"),
    @ApiResponse(responseCode = "403", description = "권한 없음 또는 본인 소유 아님"),
    @ApiResponse(responseCode = "404", description = "주소지를 찾을 수 없음")
  })
  public AddressResponse markAsDefault(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @Parameter(description = "기본 주소지로 지정할 주소지 ID", example = "1") @PathVariable Long addressId) {
    return addressService.markAsDefault(userDetails.getUserId(), addressId);
  }

  @DeleteMapping("/{addressId}")
  @Operation(
      summary = "주소지 삭제",
      description = "본인 주소지를 삭제한다. 기본 주소지를 삭제한 경우, 남은 주소 중 가장 오래된 것이 자동으로 기본 주소지로 승계된다.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "삭제 성공"),
    @ApiResponse(responseCode = "401", description = "미인증"),
    @ApiResponse(responseCode = "403", description = "권한 없음 또는 본인 소유 아님"),
    @ApiResponse(responseCode = "404", description = "주소지를 찾을 수 없음")
  })
  public ResponseEntity<Void> delete(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @Parameter(description = "주소지 ID", example = "1") @PathVariable Long addressId) {
    addressService.delete(userDetails.getUserId(), addressId);
    return ResponseEntity.noContent().build();
  }
}
