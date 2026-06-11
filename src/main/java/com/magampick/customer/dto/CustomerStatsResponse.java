package com.magampick.customer.dto;

/**
 * 소비자 마이페이지 통계 응답 DTO.
 *
 * <p>집계 정책:
 *
 * <ul>
 *   <li>monthlySavings — 이번 달(completedAt KST 기준) COMPLETED 주문의 discountTotal 합 (마감할인 전용). 환불승인 제외.
 *   <li>rescuedCount — 누적 전체 COMPLETED 주문의 items[].quantity 총합. 환불승인 제외.
 *   <li>favoriteCount — 즐겨찾기 등록 매장 수.
 * </ul>
 */
public record CustomerStatsResponse(long monthlySavings, int rescuedCount, int favoriteCount) {}
