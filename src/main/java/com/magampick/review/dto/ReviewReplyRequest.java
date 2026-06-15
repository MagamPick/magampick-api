package com.magampick.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 사장 답글 작성 요청. */
public record ReviewReplyRequest(@NotBlank @Size(max = 500) String content) {}
