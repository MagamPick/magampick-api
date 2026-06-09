package com.magampick.support.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 관리자 문의 답변 요청. */
@Schema(description = "관리자 문의 답변 요청")
public record AdminInquiryAnswerRequest(
    @Schema(description = "답변 내용 (최대 2000자)") @NotBlank @Size(max = 2000) String content) {}
