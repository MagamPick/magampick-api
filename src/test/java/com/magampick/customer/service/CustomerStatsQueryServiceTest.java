package com.magampick.customer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.magampick.customer.dto.CustomerStatsResponse;
import com.magampick.favorite.repository.FavoriteRepository;
import com.magampick.order.repository.OrderRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomerStatsQueryServiceTest {

  @Mock OrderRepository orderRepository;
  @Mock FavoriteRepository favoriteRepository;

  private CustomerStatsQueryService customerStatsQueryService;

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");
  private static final Clock FIXED_CLOCK =
      Clock.fixed(LocalDate.of(2026, 6, 15).atStartOfDay(KST).toInstant(), KST);
  private static final Long CUSTOMER_ID = 1L;

  @BeforeEach
  void setUp() {
    customerStatsQueryService =
        new CustomerStatsQueryService(orderRepository, favoriteRepository, FIXED_CLOCK);
  }

  @Test
  void 통계_조회_성공_정상_데이터() {
    // given
    LocalDateTime monthStart = LocalDate.of(2026, 6, 1).atStartOfDay();
    LocalDateTime monthEnd = LocalDate.of(2026, 7, 1).atStartOfDay();
    given(orderRepository.sumMonthlyDiscountTotal(CUSTOMER_ID, monthStart, monthEnd))
        .willReturn(BigDecimal.valueOf(14300));
    given(orderRepository.sumCumulativeRescuedItemQuantity(CUSTOMER_ID)).willReturn(8L);
    given(favoriteRepository.countByCustomerId(CUSTOMER_ID)).willReturn(4L);

    // when
    CustomerStatsResponse result = customerStatsQueryService.getStats(CUSTOMER_ID);

    // then
    assertThat(result.monthlySavings()).isEqualTo(14300L);
    assertThat(result.rescuedCount()).isEqualTo(8);
    assertThat(result.favoriteCount()).isEqualTo(4);
  }

  @Test
  void 통계_조회_성공_데이터_없으면_모두_0() {
    // given: repository 가 null 반환 (주문 없는 신규 고객)
    LocalDateTime monthStart = LocalDate.of(2026, 6, 1).atStartOfDay();
    LocalDateTime monthEnd = LocalDate.of(2026, 7, 1).atStartOfDay();
    given(orderRepository.sumMonthlyDiscountTotal(CUSTOMER_ID, monthStart, monthEnd))
        .willReturn(null);
    given(orderRepository.sumCumulativeRescuedItemQuantity(CUSTOMER_ID)).willReturn(null);
    given(favoriteRepository.countByCustomerId(CUSTOMER_ID)).willReturn(0L);

    // when
    CustomerStatsResponse result = customerStatsQueryService.getStats(CUSTOMER_ID);

    // then
    assertThat(result.monthlySavings()).isZero();
    assertThat(result.rescuedCount()).isZero();
    assertThat(result.favoriteCount()).isZero();
  }

  @Test
  void monthlySavings_이번달_KST_1일_경계로_집계() {
    // given: 2026-06-15 고정 시계 → monthStart = 06-01 00:00, monthEnd = 07-01 00:00
    LocalDateTime expectedStart = LocalDate.of(2026, 6, 1).atStartOfDay();
    LocalDateTime expectedEnd = LocalDate.of(2026, 7, 1).atStartOfDay();
    given(orderRepository.sumMonthlyDiscountTotal(CUSTOMER_ID, expectedStart, expectedEnd))
        .willReturn(BigDecimal.valueOf(5000));
    given(orderRepository.sumCumulativeRescuedItemQuantity(CUSTOMER_ID)).willReturn(null);
    given(favoriteRepository.countByCustomerId(CUSTOMER_ID)).willReturn(0L);

    // when
    CustomerStatsResponse result = customerStatsQueryService.getStats(CUSTOMER_ID);

    // then: 정확한 경계값으로 쿼리가 호출됐음을 mockito 호출 검증으로 간접 확인
    assertThat(result.monthlySavings()).isEqualTo(5000L);
  }
}
