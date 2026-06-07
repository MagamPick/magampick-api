package com.magampick.settlement.controller;

import com.magampick.settlement.dto.ProcessSettlementRequest;
import com.magampick.settlement.dto.ProcessSettlementResponse;
import com.magampick.settlement.service.SettlementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 정산 배치 트리거 API. ROLE_ADMIN 전용. */
@RestController
@RequiredArgsConstructor
@Tag(name = "Settlement (Admin)", description = "정산 배치 수동 트리거 API")
public class AdminSettlementController {

  private final SettlementService settlementService;
  private final Clock clock;

  @PostMapping("/api/v1/admin/settlements/process")
  @Operation(
      summary = "정산 배치 수동 트리거",
      description = "지정한 targetDate 기준 반월 회차 정산 처리. targetDate 미입력 시 오늘 기준. ROLE_ADMIN 인증 필요.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "배치 실행 성공"),
    @ApiResponse(responseCode = "401", description = "미인증"),
    @ApiResponse(responseCode = "403", description = "권한 없음 (ROLE_ADMIN 아님)"),
  })
  public ResponseEntity<ProcessSettlementResponse> processSettlement(
      @RequestBody(required = false) ProcessSettlementRequest request) {
    LocalDate targetDate =
        (request != null && request.targetDate() != null)
            ? request.targetDate()
            : LocalDate.now(clock);
    int count = settlementService.processBatch(targetDate);
    return ResponseEntity.ok(new ProcessSettlementResponse(count));
  }
}
