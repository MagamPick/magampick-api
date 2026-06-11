package com.magampick.customer.controller;

import com.magampick.customer.dto.CustomerLocationResponse;
import com.magampick.customer.dto.CustomerLocationUpdateRequest;
import com.magampick.customer.service.CustomerLocationService;
import com.magampick.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customers/me/location")
@RequiredArgsConstructor
@Tag(name = "Customer Location", description = "소비자 현재 위치 갱신 API")
public class CustomerLocationController {

  private final CustomerLocationService customerLocationService;

  @PutMapping
  @Operation(
      summary = "소비자 현재 위치 갱신",
      description =
          "프론트엔드가 주기적으로 호출해 소비자의 현재 위치를 저장한다."
              + " 저장된 위치가 신선(1시간 이내)한 소비자는 떨이 등록·마감임박 알림의 ② 현재위치 반경 3km 대상에 포함된다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "위치 갱신 성공"),
    @ApiResponse(responseCode = "400", description = "입력 검증 실패 (위경도 범위 오류)"),
    @ApiResponse(responseCode = "401", description = "미인증"),
    @ApiResponse(responseCode = "403", description = "소비자 역할 아님")
  })
  public CustomerLocationResponse updateLocation(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @Valid @RequestBody CustomerLocationUpdateRequest request) {
    return customerLocationService.updateLocation(
        userDetails.getUserId(), request.latitude(), request.longitude());
  }
}
