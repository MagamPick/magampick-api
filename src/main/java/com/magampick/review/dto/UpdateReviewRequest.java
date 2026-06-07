package com.magampick.review.dto;

import com.magampick.review.domain.ReviewTag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;

/** 리뷰 수정 요청. */
public record UpdateReviewRequest(
    @NotNull @Min(1) @Max(5) Integer rating,
    @Size(max = 300) String content,
    Set<ReviewTag> tags,
    @Size(max = 3) List<String> photos) {}
