package com.magampick.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;

import com.magampick.analytics.domain.AnalyticsPeriod;
import com.magampick.analytics.dto.AnalyticsResponse;
import com.magampick.analytics.dto.AnalyticsResponse.SalesBar;
import com.magampick.analytics.exception.AnalyticsErrorCode;
import com.magampick.analytics.fixture.AnalyticsFixture;
import com.magampick.customer.domain.Customer;
import com.magampick.global.exception.BusinessException;
import com.magampick.order.domain.Order;
import com.magampick.order.domain.OrderItem;
import com.magampick.order.domain.OrderStatus;
import com.magampick.order.repository.OrderRepository;
import com.magampick.review.domain.Review;
import com.magampick.review.domain.ReviewTag;
import com.magampick.review.repository.ReviewRepository;
import com.magampick.seller.domain.Seller;
import com.magampick.store.domain.Store;
import com.magampick.store.repository.StoreRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

  @Mock OrderRepository orderRepository;
  @Mock ReviewRepository reviewRepository;
  @Mock StoreRepository storeRepository;
  @Mock Clock clock;

  @InjectMocks AnalyticsService analyticsService;

  private Customer customer;
  private Seller seller;
  private Store store;

  // Clock 을 2026-06-09 09:00 KST 로 고정 (화요일)
  private static final Instant FIXED = Instant.parse("2026-06-09T00:00:00Z"); // UTC → KST 09:00
  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  @BeforeEach
  void setUp() {
    lenient().when(clock.instant()).thenReturn(FIXED);
    lenient().when(clock.getZone()).thenReturn(KST);

    customer = Customer.builder().email("c@test.com").passwordHash("x").nickname("테스터").build();
    ReflectionTestUtils.setField(customer, "id", 1L);

    seller = Seller.builder().email("s@test.com").passwordHash("x").ownerName("사장").build();
    ReflectionTestUtils.setField(seller, "id", 2L);

    store = Store.builder().seller(seller).businessNumber("0000000000").name("빵집").build();
    ReflectionTestUtils.setField(store, "id", 10L);

    lenient().when(storeRepository.findByIdAndSellerId(10L, 2L)).thenReturn(Optional.of(store));
    lenient().when(orderRepository.countOrdersByStatus(any(), any(), any())).thenReturn(List.of());
    lenient().when(reviewRepository.findForAnalytics(any(), any(), any())).thenReturn(List.of());
    lenient().when(orderRepository.sumCompletedTotalPrice(any(), any(), any())).thenReturn(null);
  }

  // ── 매출 차트: TODAY ──────────────────────────────────────────────────────────

  @Test
  void 오늘_시간대_차트_버킷() {
    // given — 14시 2건, 18시 1건
    Order o1 = completedAt(LocalDateTime.of(2026, 6, 9, 14, 0), 10000L);
    Order o2 = completedAt(LocalDateTime.of(2026, 6, 9, 14, 30), 5000L);
    Order o3 = completedAt(LocalDateTime.of(2026, 6, 9, 18, 0), 8000L);
    given(orderRepository.findCompletedForAnalytics(eq(10L), any(), any()))
        .willReturn(List.of(o1, o2, o3));

    // when
    AnalyticsResponse res = analyticsService.getAnalytics(2L, 10L, AnalyticsPeriod.TODAY);

    // then — 14시 15000, 18시 8000, 주문 없는 시간대 제외
    List<SalesBar> chart = res.sales().chart();
    assertThat(chart).hasSize(2);
    assertThat(chart.get(0).label()).isEqualTo("14시");
    assertThat(chart.get(0).amount()).isEqualTo(15000L);
    assertThat(chart.get(1).label()).isEqualTo("18시");
    assertThat(chart.get(1).amount()).isEqualTo(8000L);
  }

  // ── 매출 차트: WEEK ──────────────────────────────────────────────────────────

  @Test
  void 주_요일별_0채움_월요일시작() {
    // given — 화(10000), 목(5000). 나머지 5개 0.
    // 2026-06-09 = 화요일
    Order o1 = completedAt(LocalDateTime.of(2026, 6, 9, 12, 0), 10000L); // 화
    Order o2 = completedAt(LocalDateTime.of(2026, 6, 11, 14, 0), 5000L); // 목
    given(orderRepository.findCompletedForAnalytics(eq(10L), any(), any()))
        .willReturn(List.of(o1, o2));

    // when
    AnalyticsResponse res = analyticsService.getAnalytics(2L, 10L, AnalyticsPeriod.WEEK);

    // then — 7개, 월0 화10000 수0 목5000 금0 토0 일0
    List<SalesBar> chart = res.sales().chart();
    assertThat(chart).hasSize(7);
    assertThat(chart.get(0).label()).isEqualTo("월");
    assertThat(chart.get(0).amount()).isEqualTo(0L);
    assertThat(chart.get(1).label()).isEqualTo("화");
    assertThat(chart.get(1).amount()).isEqualTo(10000L);
    assertThat(chart.get(3).label()).isEqualTo("목");
    assertThat(chart.get(3).amount()).isEqualTo(5000L);
    assertThat(chart.get(6).label()).isEqualTo("일");
    assertThat(chart.get(6).amount()).isEqualTo(0L);
  }

  // ── 매출 차트: MONTH ─────────────────────────────────────────────────────────

  @Test
  void 달_주차_버킷() {
    // given — 1일(1주), 10일(2주), 20일(3주), 25일(4주), 30일(5주). 6월=30일→5주
    Order o1 = completedAt(LocalDateTime.of(2026, 6, 1, 10, 0), 1000L);
    Order o2 = completedAt(LocalDateTime.of(2026, 6, 10, 10, 0), 2000L);
    Order o3 = completedAt(LocalDateTime.of(2026, 6, 20, 10, 0), 3000L);
    Order o4 = completedAt(LocalDateTime.of(2026, 6, 25, 10, 0), 4000L);
    Order o5 = completedAt(LocalDateTime.of(2026, 6, 30, 10, 0), 5000L);
    given(orderRepository.findCompletedForAnalytics(eq(10L), any(), any()))
        .willReturn(List.of(o1, o2, o3, o4, o5));

    // when
    AnalyticsResponse res = analyticsService.getAnalytics(2L, 10L, AnalyticsPeriod.MONTH);

    // then — 5주 버킷, 각 1000/2000/3000/4000/5000
    List<SalesBar> chart = res.sales().chart();
    assertThat(chart).hasSize(5);
    assertThat(chart.get(0).label()).isEqualTo("1주");
    assertThat(chart.get(0).amount()).isEqualTo(1000L);
    assertThat(chart.get(4).label()).isEqualTo("5주");
    assertThat(chart.get(4).amount()).isEqualTo(5000L);
  }

  // ── 매출 차트: YEAR ──────────────────────────────────────────────────────────

  @Test
  void 올해_12개월_0채움() {
    // given — 1월, 6월에만 주문
    Order o1 = completedAt(LocalDateTime.of(2026, 1, 15, 10, 0), 10000L);
    Order o2 = completedAt(LocalDateTime.of(2026, 6, 9, 10, 0), 20000L);
    given(orderRepository.findCompletedForAnalytics(eq(10L), any(), any()))
        .willReturn(List.of(o1, o2));

    // when
    AnalyticsResponse res = analyticsService.getAnalytics(2L, 10L, AnalyticsPeriod.YEAR);

    // then — 12개, 1월=10000, 6월=20000, 나머지 0
    List<SalesBar> chart = res.sales().chart();
    assertThat(chart).hasSize(12);
    assertThat(chart.get(0).label()).isEqualTo("1월");
    assertThat(chart.get(0).amount()).isEqualTo(10000L);
    assertThat(chart.get(5).label()).isEqualTo("6월");
    assertThat(chart.get(5).amount()).isEqualTo(20000L);
    assertThat(chart.get(1).amount()).isEqualTo(0L); // 2월
    assertThat(chart.get(11).label()).isEqualTo("12월");
    assertThat(chart.get(11).amount()).isEqualTo(0L);
  }

  // ── deltaPct ─────────────────────────────────────────────────────────────────

  @Test
  void 전기매출_0이면_증감률_0() {
    // given — 현재 10000, 전기 0 (null 반환)
    given(orderRepository.findCompletedForAnalytics(eq(10L), any(), any()))
        .willReturn(List.of(completedAt(LocalDateTime.of(2026, 6, 9, 10, 0), 10000L)));
    given(orderRepository.sumCompletedTotalPrice(eq(10L), any(), any())).willReturn(null);

    // when
    AnalyticsResponse res = analyticsService.getAnalytics(2L, 10L, AnalyticsPeriod.TODAY);

    // then
    assertThat(res.sales().deltaPct()).isEqualTo(0);
  }

  @Test
  void 매출_증감률_계산() {
    // given — 현재 110000, 전기 100000 → +10%
    given(orderRepository.findCompletedForAnalytics(eq(10L), any(), any()))
        .willReturn(List.of(completedAt(LocalDateTime.of(2026, 6, 9, 10, 0), 110000L)));
    given(orderRepository.sumCompletedTotalPrice(eq(10L), any(), any()))
        .willReturn(new BigDecimal("100000"));

    // when
    AnalyticsResponse res = analyticsService.getAnalytics(2L, 10L, AnalyticsPeriod.TODAY);

    // then
    assertThat(res.sales().deltaPct()).isEqualTo(10);
  }

  // ── avgOrderValue ────────────────────────────────────────────────────────────

  @Test
  void 평균객단가_완료건수분모() {
    // given — 2건 합계 10000 → 5000
    Order o1 = completedAt(LocalDateTime.of(2026, 6, 9, 10, 0), 3000L);
    Order o2 = completedAt(LocalDateTime.of(2026, 6, 9, 11, 0), 7000L);
    given(orderRepository.findCompletedForAnalytics(eq(10L), any(), any()))
        .willReturn(List.of(o1, o2));

    // when
    AnalyticsResponse res = analyticsService.getAnalytics(2L, 10L, AnalyticsPeriod.TODAY);

    // then
    assertThat(res.sales().avgOrderValue()).isEqualTo(5000L);
  }

  // ── peakHour ─────────────────────────────────────────────────────────────────

  @Test
  void peakHour_최빈_동률이면_이른시() {
    // given — 14시 2건, 18시 2건, 10시 1건 → 14시와 18시 동률, 이른 14시
    Order o1 = completedAt(LocalDateTime.of(2026, 6, 9, 14, 0), 1000L);
    Order o2 = completedAt(LocalDateTime.of(2026, 6, 9, 14, 30), 1000L);
    Order o3 = completedAt(LocalDateTime.of(2026, 6, 9, 18, 0), 1000L);
    Order o4 = completedAt(LocalDateTime.of(2026, 6, 9, 18, 30), 1000L);
    Order o5 = completedAt(LocalDateTime.of(2026, 6, 9, 10, 0), 1000L);
    given(orderRepository.findCompletedForAnalytics(eq(10L), any(), any()))
        .willReturn(List.of(o1, o2, o3, o4, o5));

    // when
    AnalyticsResponse res = analyticsService.getAnalytics(2L, 10L, AnalyticsPeriod.TODAY);

    // then — 14시와 18시 동률 → 이른 14시
    assertThat(res.sales().peakHour()).isEqualTo("14 ~ 15시");
  }

  @Test
  void 빈데이터_peakHour_빈문자열() {
    // given — 완료 주문 없음
    given(orderRepository.findCompletedForAnalytics(eq(10L), any(), any())).willReturn(List.of());

    // when
    AnalyticsResponse res = analyticsService.getAnalytics(2L, 10L, AnalyticsPeriod.TODAY);

    // then
    assertThat(res.sales().peakHour()).isEmpty();
  }

  // ── 주문 지표 ─────────────────────────────────────────────────────────────────

  @Test
  void 거절은_취소에_합산() {
    // given — COMPLETED 2, CANCELLED 1, REJECTED 1, NO_SHOW 1
    List<Object[]> counts =
        List.of(
            new Object[] {OrderStatus.COMPLETED, 2L},
            new Object[] {OrderStatus.CANCELLED, 1L},
            new Object[] {OrderStatus.REJECTED, 1L},
            new Object[] {OrderStatus.NO_SHOW, 1L});
    given(orderRepository.countOrdersByStatus(eq(10L), any(), any())).willReturn(counts);
    given(orderRepository.findCompletedForAnalytics(eq(10L), any(), any())).willReturn(List.of());

    // when
    AnalyticsResponse res = analyticsService.getAnalytics(2L, 10L, AnalyticsPeriod.TODAY);

    // then
    assertThat(res.orders().total()).isEqualTo(5);
    assertThat(res.orders().pickedUp()).isEqualTo(2);
    assertThat(res.orders().canceled()).isEqualTo(2); // CANCELLED + REJECTED
    assertThat(res.orders().noShow()).isEqualTo(1);
  }

  @Test
  void 미결제_총주문_제외() {
    // given — 쿼리가 AWAITING_PAYMENT 를 제외하고 반환하므로 total 에 미포함됨
    // PENDING 1건만 반환 (쿼리 레벨에서 이미 제외됨)
    Object[] pendingRow = {OrderStatus.PENDING, 3L};
    List<Object[]> counts = java.util.Collections.singletonList(pendingRow);
    given(orderRepository.countOrdersByStatus(eq(10L), any(), any())).willReturn(counts);
    given(orderRepository.findCompletedForAnalytics(eq(10L), any(), any())).willReturn(List.of());

    // when
    AnalyticsResponse res = analyticsService.getAnalytics(2L, 10L, AnalyticsPeriod.TODAY);

    // then — AWAITING_PAYMENT 는 포함 안 됨, total = 3 (PENDING 만)
    assertThat(res.orders().total()).isEqualTo(3);
    assertThat(res.orders().pickedUp()).isEqualTo(0);
  }

  // ── 떨이 지표 ─────────────────────────────────────────────────────────────────

  @Test
  void 떨이_절감금액_및_가중평균할인율() {
    // given — DEAL 2개: (원가5000, 판가3000, qty2), (원가4000, 판가2000, qty1)
    // savedAmount = (5000-3000)*2 + (4000-2000)*1 = 4000+2000 = 6000
    // totalOriginal = 5000*2 + 4000*1 = 14000
    // avgDiscountRate = round(6000/14000*100) = round(42.857) = 43
    Order o = completedAt(LocalDateTime.of(2026, 6, 9, 14, 0), 8000L);
    OrderItem i1 = AnalyticsFixture.aDealItem(o, new BigDecimal("5000"), new BigDecimal("3000"), 2);
    OrderItem i2 = AnalyticsFixture.aDealItem(o, new BigDecimal("4000"), new BigDecimal("2000"), 1);
    o.addOrderItem(i1);
    o.addOrderItem(i2);
    given(orderRepository.findCompletedForAnalytics(eq(10L), any(), any())).willReturn(List.of(o));

    // when
    AnalyticsResponse res = analyticsService.getAnalytics(2L, 10L, AnalyticsPeriod.TODAY);

    // then
    assertThat(res.clearance().soldQty()).isEqualTo(3);
    assertThat(res.clearance().savedQty()).isEqualTo(3);
    assertThat(res.clearance().savedAmount()).isEqualTo(6000L);
    assertThat(res.clearance().avgDiscountRate()).isEqualTo(43);
  }

  @Test
  void 환불승인_주문_매출떨이_제외() {
    // given — findCompletedForAnalytics 가 환불승인 주문을 제외하고 반환
    // (환불승인 제외는 repo 쿼리 레벨에서 처리 — 서비스는 결과를 그대로 집계)
    // 환불승인 주문이 포함됐다면 totalSales=10000이 되겠지만 제외 후 5000만 반환
    Order o = completedAt(LocalDateTime.of(2026, 6, 9, 14, 0), 5000L);
    given(orderRepository.findCompletedForAnalytics(eq(10L), any(), any()))
        .willReturn(List.of(o)); // 환불승인 1건 제외됨

    // when
    AnalyticsResponse res = analyticsService.getAnalytics(2L, 10L, AnalyticsPeriod.TODAY);

    // then
    assertThat(res.sales().totalSales()).isEqualTo(5000L);
  }

  // ── 리뷰 지표 ─────────────────────────────────────────────────────────────────

  @Test
  void 리뷰_답글률() {
    // given — 리뷰 3건 중 2건 답글 있음 → replyRate = round(2/3*100) = 67
    Review r1 = AnalyticsFixture.aReviewWithReply(customer, null, store, 5, seller);
    Review r2 = AnalyticsFixture.aReviewWithReply(customer, null, store, 4, seller);
    Review r3 = AnalyticsFixture.aReview(customer, null, store, 3);
    given(reviewRepository.findForAnalytics(eq(10L), any(), any())).willReturn(List.of(r1, r2, r3));
    given(orderRepository.findCompletedForAnalytics(eq(10L), any(), any())).willReturn(List.of());

    // when
    AnalyticsResponse res = analyticsService.getAnalytics(2L, 10L, AnalyticsPeriod.TODAY);

    // then
    assertThat(res.review().replyRate()).isEqualTo(67);
  }

  @Test
  void 리뷰_평균별점_소수1자리() {
    // given — 별점 4, 5, 4 → avg=4.333... → 소수1자리 반올림=4.3
    Review r1 = AnalyticsFixture.aReview(customer, null, store, 4);
    Review r2 = AnalyticsFixture.aReview(customer, null, store, 5);
    Review r3 = AnalyticsFixture.aReview(customer, null, store, 4);
    given(reviewRepository.findForAnalytics(eq(10L), any(), any())).willReturn(List.of(r1, r2, r3));
    given(orderRepository.findCompletedForAnalytics(eq(10L), any(), any())).willReturn(List.of());

    // when
    AnalyticsResponse res = analyticsService.getAnalytics(2L, 10L, AnalyticsPeriod.TODAY);

    // then
    assertThat(res.review().avgRating()).isEqualTo(4.3);
  }

  @Test
  void 빠른평가_태그_7종_desc() {
    // given — DELICIOUS 3회, FRESH 1회, 나머지 0회
    Review r1 = AnalyticsFixture.aReview(customer, null, store, 5);
    Review r2 = AnalyticsFixture.aReview(customer, null, store, 4);
    Review r3 = AnalyticsFixture.aReview(customer, null, store, 4);
    AnalyticsFixture.addTags(r1, ReviewTag.DELICIOUS, ReviewTag.FRESH);
    AnalyticsFixture.addTags(r2, ReviewTag.DELICIOUS);
    AnalyticsFixture.addTags(r3, ReviewTag.DELICIOUS);
    given(reviewRepository.findForAnalytics(eq(10L), any(), any())).willReturn(List.of(r1, r2, r3));
    given(orderRepository.findCompletedForAnalytics(eq(10L), any(), any())).willReturn(List.of());

    // when
    AnalyticsResponse res = analyticsService.getAnalytics(2L, 10L, AnalyticsPeriod.TODAY);

    // then — 7종 전부, DELICIOUS(3)>FRESH(1)>나머지(0) 순
    List<AnalyticsResponse.ReviewTagCount> tags = res.review().tags();
    assertThat(tags).hasSize(7);
    assertThat(tags.get(0).tag()).isEqualTo(ReviewTag.DELICIOUS.getLabel()); // "맛있어요"
    assertThat(tags.get(0).count()).isEqualTo(3);
    assertThat(tags.get(1).tag()).isEqualTo(ReviewTag.FRESH.getLabel()); // "신선해요"
    assertThat(tags.get(1).count()).isEqualTo(1);
    // 나머지 5개 count=0
    assertThat(tags.stream().filter(t -> t.count() == 0).count()).isEqualTo(5);
  }

  // ── 권한 검증 ─────────────────────────────────────────────────────────────────

  @Test
  void 본인매장_아니면_403() {
    // given — 다른 sellerId 로 조회
    given(storeRepository.findByIdAndSellerId(10L, 999L)).willReturn(Optional.empty());

    // when / then
    assertThatThrownBy(() -> analyticsService.getAnalytics(999L, 10L, AnalyticsPeriod.TODAY))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", AnalyticsErrorCode.ANALYTICS_STORE_FORBIDDEN);
  }

  // ── 내부 유틸 검증 ─────────────────────────────────────────────────────────────

  @Test
  void 기간_TODAY_경계_검증() {
    // given — 2026-06-09 KST
    AnalyticsService.DateRange range =
        analyticsService.computeRange(AnalyticsPeriod.TODAY, java.time.LocalDate.of(2026, 6, 9));

    // then — [2026-06-09T00:00, 2026-06-10T00:00)
    assertThat(range.start()).isEqualTo(LocalDateTime.of(2026, 6, 9, 0, 0));
    assertThat(range.end()).isEqualTo(LocalDateTime.of(2026, 6, 10, 0, 0));
  }

  @Test
  void 기간_WEEK_월요일시작_경계() {
    // given — 2026-06-09(화) → 이번 주 월=06-08
    AnalyticsService.DateRange range =
        analyticsService.computeRange(AnalyticsPeriod.WEEK, java.time.LocalDate.of(2026, 6, 9));

    // then — [2026-06-08T00:00, 2026-06-15T00:00)
    assertThat(range.start()).isEqualTo(LocalDateTime.of(2026, 6, 8, 0, 0));
    assertThat(range.end()).isEqualTo(LocalDateTime.of(2026, 6, 15, 0, 0));
  }

  // ── 헬퍼 ─────────────────────────────────────────────────────────────────────

  private Order completedAt(LocalDateTime completedAt, long totalPrice) {
    return AnalyticsFixture.aCompletedOrder(
        customer, store, BigDecimal.valueOf(totalPrice), completedAt);
  }
}
