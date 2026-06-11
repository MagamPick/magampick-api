package com.magampick.customer.service;

import com.magampick.customer.dto.CustomerStatsResponse;
import com.magampick.favorite.repository.FavoriteRepository;
import com.magampick.order.repository.OrderRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 소비자 마이페이지 통계 집계 서비스. */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerStatsQueryService {

  private final OrderRepository orderRepository;
  private final FavoriteRepository favoriteRepository;
  private final Clock clock;

  /**
   * 마이페이지 통계 3종 집계.
   *
   * <ul>
   *   <li>monthlySavings — 이번 달(KST 기준) COMPLETED 주문의 discountTotal 합 (마감할인). 환불승인 제외.
   *   <li>rescuedCount — 누적 COMPLETED 주문의 items[].quantity 총합. 환불승인 제외.
   *   <li>favoriteCount — 즐겨찾기 등록 매장 수.
   * </ul>
   */
  public CustomerStatsResponse getStats(Long customerId) {
    LocalDate today = LocalDate.now(clock);
    LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();
    LocalDateTime monthEnd = monthStart.plusMonths(1);

    BigDecimal discountSum =
        orderRepository.sumMonthlyDiscountTotal(customerId, monthStart, monthEnd);
    Long itemQtySum = orderRepository.sumCumulativeRescuedItemQuantity(customerId);
    long favoriteCount = favoriteRepository.countByCustomerId(customerId);

    long monthlySavings = discountSum != null ? discountSum.longValue() : 0L;
    int rescuedCount = itemQtySum != null ? itemQtySum.intValue() : 0;

    log.info(
        "소비자 마이페이지 통계 조회됨. customerId={}, monthlySavings={}, rescuedCount={}, favoriteCount={}",
        customerId,
        monthlySavings,
        rescuedCount,
        favoriteCount);

    return new CustomerStatsResponse(monthlySavings, rescuedCount, (int) favoriteCount);
  }
}
