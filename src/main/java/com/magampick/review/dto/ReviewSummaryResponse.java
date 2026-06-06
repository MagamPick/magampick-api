package com.magampick.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "매장 리뷰 요약 (평점 평균 + 분포)")
public record ReviewSummaryResponse(
    @Schema(description = "평균 별점 (리뷰 없으면 0.0)") double average,
    @Schema(description = "총 리뷰 수") long count,
    @Schema(description = "별점 분포 (5점 → 1점 순 5개, 없는 별점도 count=0 으로 포함)")
        List<StarCount> distribution) {

  @Schema(description = "별점별 리뷰 수")
  public record StarCount(
      @Schema(description = "별점 (1-5)") int star, @Schema(description = "해당 별점 리뷰 수") long count) {}
}
