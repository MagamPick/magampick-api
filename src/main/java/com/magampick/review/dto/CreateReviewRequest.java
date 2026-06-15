package com.magampick.review.dto;

import com.magampick.review.domain.ReviewTag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;

/**
 * 리뷰 작성 요청 (multipart 의 {@code request} JSON 파트). 사진은 별도 {@code photos} File 파트로 전송되어 OCI 업로드된다 — 이
 * DTO 에는 포함하지 않는다.
 */
public record CreateReviewRequest(
    @NotNull @Min(1) @Max(5) Integer rating,
    @Size(max = 300) String content,
    Set<ReviewTag> tags) {}
