package com.magampick.analytics.service;

import com.magampick.analytics.domain.AnalyticsPeriod;
import com.magampick.analytics.dto.AnalyticsResponse;
import com.magampick.analytics.dto.AnalyticsResponse.ClearanceMetrics;
import com.magampick.analytics.dto.AnalyticsResponse.OrderMetrics;
import com.magampick.analytics.dto.AnalyticsResponse.ReviewMetrics;
import com.magampick.analytics.dto.AnalyticsResponse.ReviewTagCount;
import com.magampick.analytics.dto.AnalyticsResponse.SalesBar;
import com.magampick.analytics.dto.AnalyticsResponse.SalesMetrics;
import com.magampick.analytics.exception.AnalyticsErrorCode;
import com.magampick.global.exception.BusinessException;
import com.magampick.order.domain.ItemKind;
import com.magampick.order.domain.Order;
import com.magampick.order.domain.OrderItem;
import com.magampick.order.domain.OrderStatus;
import com.magampick.order.repository.OrderRepository;
import com.magampick.review.domain.Review;
import com.magampick.review.domain.ReviewTag;
import com.magampick.review.repository.ReviewRepository;
import com.magampick.store.repository.StoreRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 사장 통계 대시보드 집계 서비스. */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalyticsService {

  private final OrderRepository orderRepository;
  private final ReviewRepository reviewRepository;
  private final StoreRepository storeRepository;
  private final Clock clock;

  /**
   * 기간별 통계 집계. 본인 매장 검증 후 매출·주문·떨이·리뷰 지표를 반환.
   *
   * @param sellerId 인증된 사장 ID
   * @param storeId 매장 ID
   * @param period 집계 기간 단위
   */
  public AnalyticsResponse getAnalytics(Long sellerId, Long storeId, AnalyticsPeriod period) {
    verifyStoreOwnership(sellerId, storeId);

    LocalDate today = LocalDate.now(clock);
    DateRange range = computeRange(period, today);
    DateRange prevRange = computePrevRange(period, today);

    List<Order> completedOrders =
        orderRepository.findCompletedForAnalytics(storeId, range.start(), range.end());
    BigDecimal prevTotalOrNull =
        orderRepository.sumCompletedTotalPrice(storeId, prevRange.start(), prevRange.end());
    List<Object[]> statusCounts =
        orderRepository.countOrdersByStatus(storeId, range.start(), range.end());
    List<Review> reviews = reviewRepository.findForAnalytics(storeId, range.start(), range.end());

    SalesMetrics sales = buildSalesMetrics(completedOrders, prevTotalOrNull, period, range);
    OrderMetrics orders = buildOrderMetrics(statusCounts);
    ClearanceMetrics clearance = buildClearanceMetrics(completedOrders);
    ReviewMetrics review = buildReviewMetrics(reviews);

    log.info("통계 집계 완료. storeId={}, period={}, totalSales={}", storeId, period, sales.totalSales());
    return new AnalyticsResponse(sales, orders, clearance, review);
  }

  // ── 본인 매장 검증 ────────────────────────────────────────────────────────────

  private void verifyStoreOwnership(Long sellerId, Long storeId) {
    storeRepository
        .findByIdAndSellerId(storeId, sellerId)
        .orElseThrow(() -> new BusinessException(AnalyticsErrorCode.ANALYTICS_STORE_FORBIDDEN));
  }

  // ── 기간 계산 ─────────────────────────────────────────────────────────────────

  /** 기간 경계 (반열린구간 [start, end)). */
  record DateRange(LocalDateTime start, LocalDateTime end) {}

  DateRange computeRange(AnalyticsPeriod period, LocalDate today) {
    return switch (period) {
      case TODAY -> new DateRange(today.atStartOfDay(), today.plusDays(1).atStartOfDay());
      case WEEK -> {
        LocalDate monday = today.with(DayOfWeek.MONDAY);
        yield new DateRange(monday.atStartOfDay(), monday.plusWeeks(1).atStartOfDay());
      }
      case MONTH -> {
        LocalDate first = today.withDayOfMonth(1);
        yield new DateRange(first.atStartOfDay(), first.plusMonths(1).atStartOfDay());
      }
      case YEAR -> {
        LocalDate first = LocalDate.of(today.getYear(), 1, 1);
        yield new DateRange(first.atStartOfDay(), first.plusYears(1).atStartOfDay());
      }
    };
  }

  DateRange computePrevRange(AnalyticsPeriod period, LocalDate today) {
    return switch (period) {
      case TODAY -> {
        LocalDate yesterday = today.minusDays(1);
        yield new DateRange(yesterday.atStartOfDay(), today.atStartOfDay());
      }
      case WEEK -> {
        LocalDate prevMonday = today.with(DayOfWeek.MONDAY).minusWeeks(1);
        yield new DateRange(prevMonday.atStartOfDay(), prevMonday.plusWeeks(1).atStartOfDay());
      }
      case MONTH -> {
        LocalDate prevFirst = today.withDayOfMonth(1).minusMonths(1);
        yield new DateRange(prevFirst.atStartOfDay(), prevFirst.plusMonths(1).atStartOfDay());
      }
      case YEAR -> {
        LocalDate prevFirst = LocalDate.of(today.getYear() - 1, 1, 1);
        yield new DateRange(prevFirst.atStartOfDay(), prevFirst.plusYears(1).atStartOfDay());
      }
    };
  }

  // ── 매출 지표 ─────────────────────────────────────────────────────────────────

  private SalesMetrics buildSalesMetrics(
      List<Order> orders, BigDecimal prevTotalOrNull, AnalyticsPeriod period, DateRange range) {
    long totalSales = orders.stream().mapToLong(o -> o.getTotalPrice().longValue()).sum();

    long prevTotal = prevTotalOrNull != null ? prevTotalOrNull.longValue() : 0L;
    int deltaPct =
        prevTotal == 0 ? 0 : (int) Math.round((double) (totalSales - prevTotal) / prevTotal * 100);

    List<SalesBar> chart = buildChart(orders, period, range);
    long avgOrderValue = orders.isEmpty() ? 0L : Math.round((double) totalSales / orders.size());

    String peakHour = buildPeakHour(orders);

    return new SalesMetrics(totalSales, deltaPct, chart, avgOrderValue, peakHour);
  }

  private List<SalesBar> buildChart(List<Order> orders, AnalyticsPeriod period, DateRange range) {
    return switch (period) {
      case TODAY -> buildTodayChart(orders);
      case WEEK -> buildWeekChart(orders);
      case MONTH -> buildMonthChart(orders, range.start().toLocalDate());
      case YEAR -> buildYearChart(orders);
    };
  }

  /** TODAY: 주문 있는 시간대만, hour asc. */
  private List<SalesBar> buildTodayChart(List<Order> orders) {
    Map<Integer, Long> hourly =
        orders.stream()
            .collect(
                Collectors.groupingBy(
                    o -> o.getCompletedAt().getHour(),
                    Collectors.summingLong(o -> o.getTotalPrice().longValue())));
    return hourly.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .map(e -> new SalesBar(e.getKey() + "시", e.getValue()))
        .toList();
  }

  /** WEEK: 월~일 7개 전부, 0 채움. */
  private List<SalesBar> buildWeekChart(List<Order> orders) {
    String[] labels = {"월", "화", "수", "목", "금", "토", "일"};
    Map<DayOfWeek, Long> dowMap =
        orders.stream()
            .collect(
                Collectors.groupingBy(
                    o -> o.getCompletedAt().getDayOfWeek(),
                    Collectors.summingLong(o -> o.getTotalPrice().longValue())));
    List<SalesBar> chart = new ArrayList<>();
    for (DayOfWeek dow : DayOfWeek.values()) {
      chart.add(new SalesBar(labels[dow.getValue() - 1], dowMap.getOrDefault(dow, 0L)));
    }
    return chart;
  }

  /** MONTH: 주차 버킷 (1~7=1주, 8~14=2주, 15~21=3주, 22~28=4주, 29~말일=5주). 28일달=4주, 그 외=5주, 0 채움. */
  private List<SalesBar> buildMonthChart(List<Order> orders, LocalDate monthFirst) {
    Map<Integer, Long> weekMap =
        orders.stream()
            .collect(
                Collectors.groupingBy(
                    o -> weekBucket(o.getCompletedAt().getDayOfMonth()),
                    Collectors.summingLong(o -> o.getTotalPrice().longValue())));
    int maxWeek = weekBucket(monthFirst.lengthOfMonth());
    List<SalesBar> chart = new ArrayList<>();
    for (int w = 1; w <= maxWeek; w++) {
      chart.add(new SalesBar(w + "주", weekMap.getOrDefault(w, 0L)));
    }
    return chart;
  }

  /** YEAR: 1~12월 전부, 0 채움. */
  private List<SalesBar> buildYearChart(List<Order> orders) {
    Map<Month, Long> monthMap =
        orders.stream()
            .collect(
                Collectors.groupingBy(
                    o -> o.getCompletedAt().getMonth(),
                    Collectors.summingLong(o -> o.getTotalPrice().longValue())));
    List<SalesBar> chart = new ArrayList<>();
    for (Month m : Month.values()) {
      chart.add(new SalesBar(m.getValue() + "월", monthMap.getOrDefault(m, 0L)));
    }
    return chart;
  }

  /** 주차 버킷 번호 (1~5). */
  static int weekBucket(int day) {
    if (day <= 7) return 1;
    if (day <= 14) return 2;
    if (day <= 21) return 3;
    if (day <= 28) return 4;
    return 5;
  }

  /** 최다 주문 시간대. 동률이면 이른 시. 주문 없으면 빈 문자열. */
  private String buildPeakHour(List<Order> orders) {
    if (orders.isEmpty()) return "";

    Map<Integer, Long> hourCount =
        orders.stream()
            .collect(
                Collectors.groupingBy(o -> o.getCompletedAt().getHour(), Collectors.counting()));

    int peakH =
        hourCount.entrySet().stream()
            .sorted(
                Map.Entry.<Integer, Long>comparingByValue(Comparator.reverseOrder())
                    .thenComparingInt(Map.Entry::getKey))
            .findFirst()
            .orElseThrow()
            .getKey();

    return peakH + " ~ " + (peakH + 1) + "시";
  }

  // ── 주문 지표 ─────────────────────────────────────────────────────────────────

  private OrderMetrics buildOrderMetrics(List<Object[]> statusCounts) {
    int total = 0, pickedUp = 0, canceled = 0, noShow = 0;
    for (Object[] row : statusCounts) {
      OrderStatus status = (OrderStatus) row[0];
      int count = ((Number) row[1]).intValue();
      total += count;
      switch (status) {
        case COMPLETED -> pickedUp += count;
        case CANCELLED, REJECTED -> canceled += count;
        case NO_SHOW -> noShow += count;
        default -> {
          /* PENDING, PREPARING, READY → total 에만 합산 */
        }
      }
    }
    return new OrderMetrics(total, pickedUp, canceled, noShow);
  }

  // ── 떨이 지표 ─────────────────────────────────────────────────────────────────

  private ClearanceMetrics buildClearanceMetrics(List<Order> orders) {
    List<OrderItem> dealItems =
        orders.stream()
            .flatMap(o -> o.getOrderItems().stream())
            .filter(i -> ItemKind.DEAL == i.getItemKind())
            .toList();

    int soldQty = dealItems.stream().mapToInt(OrderItem::getQuantity).sum();

    long savedAmount =
        dealItems.stream()
            .mapToLong(
                i ->
                    i.getOriginalPrice()
                        .subtract(i.getUnitPrice())
                        .multiply(BigDecimal.valueOf(i.getQuantity()))
                        .longValue())
            .sum();

    long totalOriginal =
        dealItems.stream()
            .mapToLong(
                i -> i.getOriginalPrice().multiply(BigDecimal.valueOf(i.getQuantity())).longValue())
            .sum();

    int avgDiscountRate =
        totalOriginal == 0 ? 0 : (int) Math.round((double) savedAmount / totalOriginal * 100);

    return new ClearanceMetrics(soldQty, soldQty, savedAmount, avgDiscountRate);
  }

  // ── 리뷰 지표 ─────────────────────────────────────────────────────────────────

  private ReviewMetrics buildReviewMetrics(List<Review> reviews) {
    int newCount = reviews.size();

    double avgRating = 0.0;
    if (newCount > 0) {
      double raw = reviews.stream().mapToInt(Review::getRating).average().orElse(0.0);
      avgRating = Math.round(raw * 10.0) / 10.0;
    }

    int replyCount = (int) reviews.stream().filter(Review::hasReply).count();
    int replyRate = newCount == 0 ? 0 : (int) Math.round((double) replyCount / newCount * 100);

    Map<ReviewTag, Long> tagCounts =
        reviews.stream()
            .flatMap(r -> r.getTags().stream())
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

    List<ReviewTagCount> tags =
        Arrays.stream(ReviewTag.values())
            .sorted(
                Comparator.comparingLong((ReviewTag t) -> tagCounts.getOrDefault(t, 0L))
                    .reversed()
                    .thenComparingInt(ReviewTag::ordinal))
            .map(t -> new ReviewTagCount(t.getLabel(), (int) (long) tagCounts.getOrDefault(t, 0L)))
            .toList();

    return new ReviewMetrics(avgRating, newCount, replyRate, tags);
  }
}
