package com.magampick.support.dto;

import com.magampick.support.domain.InquiryCategory;
import com.magampick.support.domain.InquiryStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/** 문의 응답 DTO. createdAt 은 LocalDate (yyyy-MM-dd). answer 는 답변 전 null. */
@Schema(description = "문의 응답")
public record InquiryResponse(
    @Schema(description = "문의 ID") Long id,
    @Schema(description = "카테고리") InquiryCategory category,
    @Schema(description = "제목") String title,
    @Schema(description = "내용") String content,
    @Schema(description = "상태 (pending / answered)") InquiryStatus status,
    @Schema(description = "접수일 (yyyy-MM-dd)") LocalDate createdAt,
    @Schema(description = "답변 (없으면 null)") InquiryAnswerResponse answer) {}
