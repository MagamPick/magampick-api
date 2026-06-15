package com.magampick.support.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/** 문의 답변 응답. answeredAt 은 LocalDate (yyyy-MM-dd). */
@Schema(description = "문의 답변")
public record InquiryAnswerResponse(
    @Schema(description = "답변 내용") String content,
    @Schema(description = "답변일 (yyyy-MM-dd)") LocalDate answeredAt) {}
