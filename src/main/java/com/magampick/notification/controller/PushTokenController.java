package com.magampick.notification.controller;

import com.magampick.global.security.CustomUserDetails;
import com.magampick.notification.dto.PushTokenDeleteRequest;
import com.magampick.notification.dto.PushTokenRegisterRequest;
import com.magampick.notification.dto.PushTokenResponse;
import com.magampick.notification.service.PushTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/push-tokens")
@RequiredArgsConstructor
@Tag(name = "Push Token", description = "FCM 푸시 토큰 등록/해제 API (소비자·사장 공용)")
public class PushTokenController {

  private final PushTokenService pushTokenService;

  @PostMapping
  @Operation(
      summary = "FCM 토큰 등록",
      description = "디바이스의 FCM 토큰을 등록(upsert). 로그인 후 호출. 같은 토큰 재등록 시 소유자 재할당.")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "등록 성공"),
    @ApiResponse(responseCode = "400", description = "입력 검증 실패"),
    @ApiResponse(responseCode = "401", description = "미인증")
  })
  public ResponseEntity<PushTokenResponse> register(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @RequestBody @Valid PushTokenRegisterRequest request) {
    PushTokenResponse response =
        pushTokenService.register(userDetails.getRole(), userDetails.getUserId(), request.token());
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @DeleteMapping
  @Operation(summary = "FCM 토큰 해제", description = "디바이스의 FCM 토큰을 해제. 로그아웃 시 호출. 미등록 토큰이어도 204.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "해제 성공"),
    @ApiResponse(responseCode = "400", description = "입력 검증 실패"),
    @ApiResponse(responseCode = "401", description = "미인증")
  })
  public ResponseEntity<Void> unregister(@RequestBody @Valid PushTokenDeleteRequest request) {
    pushTokenService.unregister(request.token());
    return ResponseEntity.noContent().build();
  }
}
