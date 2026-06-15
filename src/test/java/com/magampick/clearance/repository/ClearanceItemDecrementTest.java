package com.magampick.clearance.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.magampick.TestcontainersConfiguration;
import com.magampick.clearance.domain.ClearanceItem;
import com.magampick.clearance.domain.ClearanceItemStatus;
import com.magampick.global.common.GeometryUtil;
import com.magampick.global.config.JpaAuditingConfig;
import com.magampick.seller.domain.Seller;
import com.magampick.seller.repository.SellerRepository;
import com.magampick.store.domain.OperationStatus;
import com.magampick.store.domain.Store;
import com.magampick.store.repository.StoreRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * ClearanceItemRepository.decrementStock — 조건부 UPDATE 검증. 동시성 안전: remaining >= qty 조건이 DB 레벨에서
 * 보장되는지 실제 SQL 로 확인.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
class ClearanceItemDecrementTest {

  @Autowired ClearanceItemRepository clearanceItemRepository;
  @Autowired StoreRepository storeRepository;
  @Autowired SellerRepository sellerRepository;

  private Store store;

  @BeforeEach
  void setUp() {
    Seller seller =
        sellerRepository.save(
            Seller.builder()
                .email("seller_" + System.nanoTime() + "@test.com")
                .passwordHash("x")
                .ownerName("테스트사장")
                .build());

    store =
        storeRepository.save(
            Store.builder()
                .seller(seller)
                .businessNumber("1234567890")
                .representativeName("테스트사장")
                .openDate(LocalDate.of(2020, 1, 1))
                .name("테스트매장")
                .roadAddress("서울시 강남구 테헤란로 1")
                .zonecode("06158")
                .location(GeometryUtil.toPoint(37.5, 127.0))
                .phone("0212345678")
                .operationStatus(OperationStatus.OPEN)
                .build());
  }

  @Test
  void 재고_충분시_차감_성공() {
    // given
    ClearanceItem item = clearanceItemRepository.save(aClearanceItem(10));

    // when
    int updated = clearanceItemRepository.decrementStock(item.getId(), 3);

    // then
    assertThat(updated).isEqualTo(1);
    ClearanceItem refreshed = clearanceItemRepository.findById(item.getId()).orElseThrow();
    assertThat(refreshed.getRemainingQuantity()).isEqualTo(7);
  }

  @Test
  void 재고_부족시_차감_실패() {
    // given — remainingQuantity = 2
    ClearanceItem item = clearanceItemRepository.save(aClearanceItem(2));

    // when — 3개 차감 시도
    int updated = clearanceItemRepository.decrementStock(item.getId(), 3);

    // then — 영향 행 0 = 실패
    assertThat(updated).isEqualTo(0);
    ClearanceItem unchanged = clearanceItemRepository.findById(item.getId()).orElseThrow();
    assertThat(unchanged.getRemainingQuantity()).isEqualTo(2); // 변경 없음
  }

  @Test
  void 재고_정확히_같을때_차감_성공() {
    // given — remainingQuantity = 5
    ClearanceItem item = clearanceItemRepository.save(aClearanceItem(5));

    // when — 5개 차감 (정확히 일치)
    int updated = clearanceItemRepository.decrementStock(item.getId(), 5);

    // then
    assertThat(updated).isEqualTo(1);
    ClearanceItem refreshed = clearanceItemRepository.findById(item.getId()).orElseThrow();
    assertThat(refreshed.getRemainingQuantity()).isEqualTo(0);
  }

  @Test
  void 재고_소진시_SOLD_OUT_전이() {
    // given — remainingQuantity = 2
    ClearanceItem item = clearanceItemRepository.save(aClearanceItem(2));

    // when — 2개 차감 (정확히 소진)
    int updated = clearanceItemRepository.decrementStock(item.getId(), 2);

    // then — remaining=0 이고 status=SOLD_OUT 으로 전이
    assertThat(updated).isEqualTo(1);
    ClearanceItem refreshed = clearanceItemRepository.findById(item.getId()).orElseThrow();
    assertThat(refreshed.getRemainingQuantity()).isEqualTo(0);
    assertThat(refreshed.getStatus()).isEqualTo(ClearanceItemStatus.SOLD_OUT);
    assertThat(refreshed.getCloseReason())
        .isEqualTo(com.magampick.clearance.domain.ClearanceCloseReason.SOLD_OUT);
  }

  @Test
  void 재고_남으면_OPEN_유지() {
    // given — remainingQuantity = 5
    ClearanceItem item = clearanceItemRepository.save(aClearanceItem(5));

    // when — 2개 차감 (잔량 3 남음)
    int updated = clearanceItemRepository.decrementStock(item.getId(), 2);

    // then — remaining=3 이고 status=OPEN 유지
    assertThat(updated).isEqualTo(1);
    ClearanceItem refreshed = clearanceItemRepository.findById(item.getId()).orElseThrow();
    assertThat(refreshed.getRemainingQuantity()).isEqualTo(3);
    assertThat(refreshed.getStatus()).isEqualTo(ClearanceItemStatus.OPEN);
  }

  private ClearanceItem aClearanceItem(int totalQuantity) {
    return ClearanceItem.builder()
        .store(store)
        .name("크로아상_" + System.nanoTime())
        .regularPrice(new BigDecimal("4500"))
        .salePrice(new BigDecimal("3000"))
        .totalQuantity(totalQuantity)
        .pickupStartAt(LocalDateTime.now().minusHours(1))
        .pickupEndAt(LocalDateTime.now().plusHours(3))
        .build();
  }
}
