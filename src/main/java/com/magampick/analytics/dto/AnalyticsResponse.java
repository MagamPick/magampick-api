package com.magampick.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 사장 통계 대시보드 응답. FE 계약 필드명 1:1 camelCase. */
@Schema(description = "사장 통계 대시보드 응답")
public record AnalyticsResponse(
    @Schema(description = "매출 지표") SalesMetrics sales,
    @Schema(description = "주문 지표") OrderMetrics orders,
    @Schema(description = "떨이 지표") ClearanceMetrics clearance,
    @Schema(description = "리뷰 지표") ReviewMetrics review) {

  @Schema(description = "매출 지표")
  public record SalesMetrics(
      @Schema(description = "총 매출") long totalSales,
      @Schema(description = "전기 대비 증감률(%)") int deltaPct,
      @Schema(description = "기간별 차트 버킷") List<SalesBar> chart,
      @Schema(description = "평균 객단가") long avgOrderValue,
      @Schema(description = "최다 주문 시간대 (예: 18 ~ 19시)") String peakHour) {}

  @Schema(description = "매출 차트 항목")
  public record SalesBar(
      @Schema(description = "버킷 라벨 (예: 18시, 월, 1주, 6월)") String label,
      @Schema(description = "금액") long amount) {}

  @Schema(description = "주문 지표")
  public record OrderMetrics(
      @Schema(description = "총 주문수 (미결제 제외)") int total,
      @Schema(description = "수령완료") int pickedUp,
      @Schema(description = "취소+거절") int canceled,
      @Schema(description = "미수령") int noShow) {}

  @Schema(description = "떨이 지표")
  public record ClearanceMetrics(
      @Schema(description = "판매 수량") int soldQty,
      @Schema(description = "구한 수량 (soldQty와 동일)") int savedQty,
      @Schema(description = "절감 금액") long savedAmount,
      @Schema(description = "가중평균 할인율(%)") int avgDiscountRate) {}

  @Schema(description = "리뷰 지표")
  public record ReviewMetrics(
      @Schema(description = "평균 별점 (소수 1자리)") double avgRating,
      @Schema(description = "신규 리뷰 수") int newCount,
      @Schema(description = "답글률(%)") int replyRate,
      @Schema(description = "태그별 카운트 (7종 전부, count desc)") List<ReviewTagCount> tags) {}

  @Schema(description = "리뷰 태그 카운트")
  public record ReviewTagCount(
      @Schema(description = "태그 한글 라벨") String tag, @Schema(description = "카운트") int count) {}
}
