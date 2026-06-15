package com.magampick.terms.controller;

import com.magampick.terms.dto.TermResponse;
import com.magampick.terms.service.TermService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/terms")
@RequiredArgsConstructor
@Tag(name = "Term", description = "약관 조회 API")
public class TermController {

  private final TermService termService;

  @GetMapping
  @Operation(summary = "약관 목록 조회", description = "회원가입 화면에 표시할 약관 목록(필수 + 선택)을 조회한다.")
  @ApiResponses(@ApiResponse(responseCode = "200", description = "조회 성공"))
  public List<TermResponse> getTerms(
      @Parameter(description = "가입 역할. SELLER면 사장 약관(AGE_19)을 조회", example = "SELLER")
          @RequestParam(defaultValue = "CUSTOMER")
          String role) {
    if ("SELLER".equalsIgnoreCase(role)) {
      return termService.getTermsForSellerSignup();
    }
    return termService.getTermsForSignup();
  }
}
