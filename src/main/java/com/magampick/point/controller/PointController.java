package com.magampick.point.controller;

import com.magampick.global.security.CustomUserDetails;
import com.magampick.point.dto.PointHistoryFilter;
import com.magampick.point.dto.PointSummaryResponse;
import com.magampick.point.dto.PointTransactionResponse;
import com.magampick.point.service.PointService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customers/me/points")
@RequiredArgsConstructor
@Tag(name = "Point (Customer)", description = "소비자 포인트 조회 API")
public class PointController {

  private final PointService pointService;

  @GetMapping("/summary")
  @Operation(summary = "포인트 잔액 조회")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "조회 성공"),
    @ApiResponse(responseCode = "401", description = "미인증")
  })
  public PointSummaryResponse getSummary(@AuthenticationPrincipal CustomUserDetails userDetails) {
    return pointService.getSummary(userDetails.getUserId());
  }

  @GetMapping("/history")
  @Operation(summary = "포인트 내역 조회")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "조회 성공"),
    @ApiResponse(responseCode = "401", description = "미인증")
  })
  public List<PointTransactionResponse> getHistory(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @RequestParam(defaultValue = "ALL") PointHistoryFilter filter) {
    return pointService.getHistory(userDetails.getUserId(), filter);
  }
}
