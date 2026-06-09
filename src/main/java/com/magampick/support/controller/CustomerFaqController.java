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

/** 소비자 FAQ 조회 API. /api/v1/faqs — anyRequest().authenticated() 커버. */
@RestController
@RequestMapping("/api/v1/faqs")
@RequiredArgsConstructor
@Tag(name = "Support (Customer)", description = "소비자 FAQ 조회 API — 인증 필요")
public class CustomerFaqController {

  private final SupportService supportService;

  @GetMapping
  @Operation(summary = "소비자 FAQ 목록", description = "소비자 대상 FAQ 목록. sortOrder 오름차순.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "조회 성공"),
    @ApiResponse(responseCode = "401", description = "미인증")
  })
  public List<FaqResponse> list() {
    return supportService.listFaqs(Role.CUSTOMER);
  }
}
