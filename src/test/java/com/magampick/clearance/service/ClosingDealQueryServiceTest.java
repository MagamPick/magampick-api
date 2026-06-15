package com.magampick.clearance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.magampick.address.exception.AddressErrorCode;
import com.magampick.address.service.AddressService;
import com.magampick.clearance.dto.ClosingDealResponse;
import com.magampick.clearance.repository.ClearanceItemRepository;
import com.magampick.clearance.repository.ClosingDealCandidate;
import com.magampick.global.common.GeometryUtil;
import com.magampick.global.exception.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClosingDealQueryServiceTest {

  @Mock AddressService addressService;
  @Mock ClearanceItemRepository clearanceItemRepository;
  @InjectMocks ClosingDealQueryService service;

  private static final Long CUSTOMER_ID = 1L;
  private static final double LAT = 37.5665;
  private static final double LNG = 126.9780;

  // ── 기본 주소지 없음 ──────────────────────────────────────────────────────────────────────────────

  @Test
  void 기본주소지_없으면_DEFAULT_ADDRESS_REQUIRED() {
    given(addressService.requireDefaultLocation(CUSTOMER_ID))
        .willThrow(new BusinessException(AddressErrorCode.DEFAULT_ADDRESS_REQUIRED));

    assertThatThrownBy(() -> service.getClosingSoonDeals(CUSTOMER_ID))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(AddressErrorCode.DEFAULT_ADDRESS_REQUIRED);
  }

  // ── 빈 결과 ────────────────────────────────────────────────────────────────────────────────────

  @Test
  void 빈_결과_빈_리스트() {
    stubAddress();
    given(
            clearanceItemRepository.findClosingSoonDeals(
                anyDouble(), anyDouble(), anyString(), any(), any()))
        .willReturn(List.of());

    List<ClosingDealResponse> result = service.getClosingSoonDeals(CUSTOMER_ID);

    assertThat(result).isEmpty();
  }

  // ── discountRate 계산 ─────────────────────────────────────────────────────────────────────────

  @Test
  void discountRate_30퍼센트_계산() {
    // regular=10000, sale=7000 → (10000-7000)/10000 * 100 = 30%
    stubAddress();
    ClosingDealCandidate c =
        candidate(
            1L,
            "매장",
            "상품",
            null,
            new BigDecimal("10000"),
            new BigDecimal("7000"),
            LocalDateTime.now().plusMinutes(30));
    given(
            clearanceItemRepository.findClosingSoonDeals(
                anyDouble(), anyDouble(), anyString(), any(), any()))
        .willReturn(List.of(c));

    List<ClosingDealResponse> result = service.getClosingSoonDeals(CUSTOMER_ID);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).discountRate()).isEqualTo(30);
  }

  @Test
  void discountRate_반올림_33퍼센트() {
    // regular=4500, sale=3000 → (4500-3000)/4500 * 100 ≈ 33.33 → 33
    stubAddress();
    ClosingDealCandidate c =
        candidate(
            2L,
            "매장",
            "상품",
            null,
            new BigDecimal("4500"),
            new BigDecimal("3000"),
            LocalDateTime.now().plusMinutes(20));
    given(
            clearanceItemRepository.findClosingSoonDeals(
                anyDouble(), anyDouble(), anyString(), any(), any()))
        .willReturn(List.of(c));

    List<ClosingDealResponse> result = service.getClosingSoonDeals(CUSTOMER_ID);

    assertThat(result.get(0).discountRate()).isEqualTo(33);
  }

  // ── 마감순 정렬 (쿼리 ASC 결과를 그대로 유지) ─────────────────────────────────────────────────────

  @Test
  void 마감순_쿼리_결과_순서_유지() {
    stubAddress();
    LocalDateTime sooner = LocalDateTime.now().plusMinutes(10);
    LocalDateTime later = LocalDateTime.now().plusMinutes(50);
    ClosingDealCandidate c1 =
        candidate(1L, "매장A", "상품A", null, new BigDecimal("5000"), new BigDecimal("3500"), sooner);
    ClosingDealCandidate c2 =
        candidate(2L, "매장B", "상품B", null, new BigDecimal("5000"), new BigDecimal("3500"), later);
    given(
            clearanceItemRepository.findClosingSoonDeals(
                anyDouble(), anyDouble(), anyString(), any(), any()))
        .willReturn(List.of(c1, c2)); // 쿼리 ORDER BY pickupEndAt ASC 결과

    List<ClosingDealResponse> result = service.getClosingSoonDeals(CUSTOMER_ID);

    assertThat(result).hasSize(2);
    assertThat(result.get(0).id()).isEqualTo(1L); // 먼저 마감
    assertThat(result.get(0).pickupDeadline()).isEqualTo(sooner);
    assertThat(result.get(1).id()).isEqualTo(2L);
  }

  // ── 응답 필드 매핑 ────────────────────────────────────────────────────────────────────────────

  @Test
  void 응답_필드_전체_매핑_정확() {
    stubAddress();
    LocalDateTime deadline = LocalDateTime.now().plusMinutes(45);
    ClosingDealCandidate c =
        candidate(
            10L,
            "우리빵집",
            "크로아상",
            "/img/croissant.jpg",
            new BigDecimal("4500"),
            new BigDecimal("3000"),
            deadline);
    given(
            clearanceItemRepository.findClosingSoonDeals(
                anyDouble(), anyDouble(), anyString(), any(), any()))
        .willReturn(List.of(c));

    List<ClosingDealResponse> result = service.getClosingSoonDeals(CUSTOMER_ID);

    ClosingDealResponse r = result.get(0);
    assertThat(r.id()).isEqualTo(10L);
    assertThat(r.storeName()).isEqualTo("우리빵집");
    assertThat(r.productName()).isEqualTo("크로아상");
    assertThat(r.imageUrl()).isEqualTo("/img/croissant.jpg");
    assertThat(r.originalPrice()).isEqualByComparingTo(new BigDecimal("4500"));
    assertThat(r.salePrice()).isEqualByComparingTo(new BigDecimal("3000"));
    assertThat(r.pickupDeadline()).isEqualTo(deadline);
  }

  @Test
  void imageUrl_null_허용() {
    stubAddress();
    ClosingDealCandidate c =
        candidate(
            3L,
            "매장",
            "상품",
            null,
            new BigDecimal("3000"),
            new BigDecimal("2000"),
            LocalDateTime.now().plusMinutes(30));
    given(
            clearanceItemRepository.findClosingSoonDeals(
                anyDouble(), anyDouble(), anyString(), any(), any()))
        .willReturn(List.of(c));

    List<ClosingDealResponse> result = service.getClosingSoonDeals(CUSTOMER_ID);

    assertThat(result.get(0).imageUrl()).isNull();
  }

  // ── helper ───────────────────────────────────────────────────────────────────────────────────

  private void stubAddress() {
    given(addressService.requireDefaultLocation(CUSTOMER_ID))
        .willReturn(GeometryUtil.toPoint(LAT, LNG));
  }

  private ClosingDealCandidate candidate(
      Long id,
      String storeName,
      String productName,
      String imageUrl,
      BigDecimal regular,
      BigDecimal sale,
      LocalDateTime deadline) {
    return new ClosingDealCandidate() {
      @Override
      public Long getId() {
        return id;
      }

      @Override
      public String getStoreName() {
        return storeName;
      }

      @Override
      public String getProductName() {
        return productName;
      }

      @Override
      public String getImageUrl() {
        return imageUrl;
      }

      @Override
      public BigDecimal getRegularPrice() {
        return regular;
      }

      @Override
      public BigDecimal getSalePrice() {
        return sale;
      }

      @Override
      public LocalDateTime getPickupDeadline() {
        return deadline;
      }
    };
  }
}
