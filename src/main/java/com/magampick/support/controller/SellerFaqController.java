package com.magampick.support.controller;

import com.magampick.global.security.Role;
import com.magampick.support.dto.FaqResponse;
import com.magampick.support.service.SupportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 사장 FAQ 조회 API. /api/v1/seller/faqs — SecurityConfig 의 hasRole(SELLER) 로 보호됨. */
@RestController
@RequestMapping("/api/v1/seller/faqs")
@RequiredArgsConstructor
@Tag(name = "Support (Seller)", description = "사장 FAQ 조회 API — ROLE_SELLER 전용")
public class SellerFaqController {

  private final SupportService supportService;

  @GetMapping
  @Operation(summary = "사장 FAQ 목록", description = "사장 대상 FAQ 목록. sortOrder 오름차순.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "조회 성공"),
    @ApiResponse(responseCode = "401", description = "미인증"),
    @ApiResponse(responseCode = "403", description = "권한 없음")
  })
  public List<FaqResponse> list() {
    return supportService.listFaqs(Role.SELLER);
  }
}
