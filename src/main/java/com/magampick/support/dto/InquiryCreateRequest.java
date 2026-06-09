package com.magampick.support.dto;

import com.magampick.support.domain.InquiryCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 문의 생성 요청. */
@Schema(description = "문의 생성 요청")
public record InquiryCreateRequest(
    @Schema(
            description =
                "카테고리 (payment / order / coupon / account / report / settlement / store / product / etc)")
        @NotNull
        InquiryCategory category,
    @Schema(description = "제목 (2~40자)") @NotBlank @Size(min = 2, max = 40) String title,
    @Schema(description = "내용 (10~1000자)") @NotBlank @Size(min = 10, max = 1000) String content) {}
