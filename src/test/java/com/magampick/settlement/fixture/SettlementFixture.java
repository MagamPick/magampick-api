package com.magampick.settlement.fixture;

import com.magampick.global.common.GeometryUtil;
import com.magampick.seller.domain.Seller;
import com.magampick.settlement.domain.Settlement;
import com.magampick.settlement.domain.SettlementStatus;
import com.magampick.settlement.dto.SettlementCycleResponse;
import com.magampick.settlement.dto.SettlementSummaryResponse;
import com.magampick.store.domain.OperationStatus;
import com.magampick.store.domain.Store;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.test.util.ReflectionTestUtils;

/** 정산 도메인 테스트 픽스처. */
public class SettlementFixture {

  private SettlementFixture() {}

  public static Seller aSeller() {
    Seller s =
        Seller.builder().email("seller@test.com").passwordHash("hash").ownerName("사장님").build();
    ReflectionTestUtils.setField(s, "id", 2L);
    return s;
  }

  public static Store aStore() {
    Store s =
        Store.builder()
            .seller(aSeller())
            .businessNumber("1234567890")
            .name("동네빵집")
            .roadAddress("서울 강남구 테헤란로 1")
            .zonecode("06158")
            .location(GeometryUtil.toPoint(37.5, 127.0))
            .phone("0212345678")
            .operationStatus(OperationStatus.OPEN)
            .build();
    ReflectionTestUtils.setField(s, "id", 10L);
    return s;
  }

  /** 2026년 6월 1차(1~15일) 정산, SCHEDULED 상태. */
  public static Settlement aSettlement(Store store) {
    Settlement s =
        Settlement.builder()
            .store(store)
            .year(2026)
            .month(6)
            .half(1)
            .periodStart(LocalDate.of(2026, 6, 1))
            .periodEnd(LocalDate.of(2026, 6, 15))
            .depositDate(LocalDate.of(2026, 6, 25))
            .grossAmount(new BigDecimal("1000000"))
            .feeAmount(new BigDecimal("65000"))
            .netAmount(new BigDecimal("935000"))
            .status(SettlementStatus.SCHEDULED)
            .build();
    ReflectionTestUtils.setField(s, "id", 1L);
    return s;
  }

  /** 2026년 6월 2차(16~30일) 정산, SCHEDULED 상태. */
  public static Settlement aSettlementHalf2(Store store) {
    Settlement s =
        Settlement.builder()
            .store(store)
            .year(2026)
            .month(6)
            .half(2)
            .periodStart(LocalDate.of(2026, 6, 16))
            .periodEnd(LocalDate.of(2026, 6, 30))
            .depositDate(LocalDate.of(2026, 7, 10))
            .grossAmount(new BigDecimal("500000"))
            .feeAmount(new BigDecimal("32500"))
            .netAmount(new BigDecimal("467500"))
            .status(SettlementStatus.SCHEDULED)
            .build();
    ReflectionTestUtils.setField(s, "id", 2L);
    return s;
  }

  public static SettlementCycleResponse aCycleResponse(Long id) {
    return new SettlementCycleResponse(
        id,
        10L,
        2026,
        6,
        1,
        LocalDate.of(2026, 6, 1).atStartOfDay().atOffset(ZoneOffset.ofHours(9)),
        LocalDate.of(2026, 6, 15).atStartOfDay().atOffset(ZoneOffset.ofHours(9)),
        LocalDate.of(2026, 6, 25).atStartOfDay().atOffset(ZoneOffset.ofHours(9)),
        new BigDecimal("1000000"),
        new BigDecimal("65000"),
        new BigDecimal("935000"),
        "SCHEDULED");
  }

  public static SettlementSummaryResponse aSummaryResponse(Long cycleId) {
    return new SettlementSummaryResponse(
        cycleId,
        "6월 1차 · 6/1~6/15",
        new BigDecimal("935000"),
        LocalDate.of(2026, 6, 25).atStartOfDay().atOffset(ZoneOffset.ofHours(9)),
        "SCHEDULED");
  }
}
