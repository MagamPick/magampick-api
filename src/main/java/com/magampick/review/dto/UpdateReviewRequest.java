package com.magampick.review.dto;

import com.magampick.review.domain.ReviewTag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;

/**
 * 리뷰 수정 요청 (multipart 의 {@code request} JSON 파트). 유지할 기존 사진은 {@code keepImageUrls} 로, 새로 추가할 사진은 별도
 * {@code photos} File 파트로 전송한다. 최종 사진 = {@code keepImageUrls} + 새 업로드 URL (순서대로, 합 최대 3장).
 */
public record UpdateReviewRequest(
    @NotNull @Min(1) @Max(5) Integer rating,
    @Size(max = 300) String content,
    Set<ReviewTag> tags,
    List<String> keepImageUrls) {}
