package com.magampick.support.controller;

import com.magampick.global.response.PageResponse;
import com.magampick.support.domain.InquiryCategory;
import com.magampick.support.domain.InquiryStatus;
import com.magampick.support.dto.AdminInquiryAnswerRequest;
import com.magampick.support.dto.InquiryResponse;
import com.magampick.support.service.SupportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 문의 API. /api/v1/admin/** — SecurityConfig 의 hasRole(ADMIN) 로 보호됨. */
@RestController
@RequestMapping("/api/v1/admin/inquiries")
@RequiredArgsConstructor
@Tag(name = "Support (Admin)", description = "관리자 문의 관리 API — ROLE_ADMIN 전용")
public class AdminInquiryController {

  private final SupportService supportService;

  @GetMapping
  @Operation(
      summary = "관리자 문의 목록",
      description = "전체 문의 목록. PENDING 우선 → createdAt DESC. status / category 필터 가능.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "조회 성공"),
    @ApiResponse(responseCode = "401", description = "미인증"),
    @ApiResponse(responseCode = "403", description = "권한 없음")
  })
  public PageResponse<InquiryResponse> list(
      @RequestParam(required = false) InquiryStatus status,
      @RequestParam(required = false) InquiryCategory category,
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return supportService.listInquiriesForAdmin(status, category, pageable);
  }

  @PostMapping("/{inquiryId}/answer")
  @Operation(
      summary = "문의 답변",
      description = "관리자 전용. PENDING 문의에만 가능. 답변 후 작성자에게 always-on 알림 발송.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "답변 성공"),
    @ApiResponse(responseCode = "400", description = "입력 검증 실패"),
    @ApiResponse(responseCode = "401", description = "미인증"),
    @ApiResponse(responseCode = "403", description = "권한 없음"),
    @ApiResponse(responseCode = "404", description = "문의 없음"),
    @ApiResponse(responseCode = "409", description = "이미 답변된 문의")
  })
  public InquiryResponse answer(
      @PathVariable Long inquiryId, @Valid @RequestBody AdminInquiryAnswerRequest request) {
    return supportService.answerInquiry(inquiryId, request);
  }
}
