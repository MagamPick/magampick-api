package com.magampick.clearance.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

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
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
class ClearanceItemRepositoryTest {

  @Autowired ClearanceItemRepository clearanceItemRepository;
  @Autowired StoreRepository storeRepository;
  @Autowired SellerRepository sellerRepository;

  private Store storeA;
  private Store storeB;

  @BeforeEach
  void setUp() {
    // @DataJpaTest 는 @Transactional 이므로 각 테스트 후 롤백 — 수동 cleanup 불필요
    Seller seller =
        sellerRepository.save(
            Seller.builder()
                .email("seller_" + System.nanoTime() + "@test.com")
                .passwordHash("x")
                .ownerName("테스트사장")
                .build());

    storeA = saveStore(seller, "매장A");
    storeB = saveStore(seller, "매장B");
  }

  @Test
  void 활성_떨이_배치_집계_count_정확() {
    saveItem(storeA, "1000", "700", tomorrowAt(15, 0), ClearanceItemStatus.OPEN);
    saveItem(storeA, "2000", "1000", tomorrowAt(16, 0), ClearanceItemStatus.OPEN);
    saveItem(storeA, "3000", "2000", tomorrowAt(17, 0), ClearanceItemStatus.CLOSED); // 제외
    clearanceItemRepository.flush();

    List<Object[]> result =
        clearanceItemRepository.findActiveDealSummaryByStoreIds(
            List.of(storeA.getId()), ClearanceItemStatus.OPEN);

    assertThat(result).hasSize(1);
    Object[] row = result.get(0);
    assertThat(((Number) row[0]).longValue()).isEqualTo(storeA.getId());
    assertThat(((Number) row[1]).longValue()).isEqualTo(2L); // OPEN 2개
  }

  @Test
  void 활성_떨이_없는_매장은_결과에_없음() {
    saveItem(storeA, "1000", "700", tomorrowAt(15, 0), ClearanceItemStatus.CLOSED);
    clearanceItemRepository.flush();

    List<Object[]> result =
        clearanceItemRepository.findActiveDealSummaryByStoreIds(
            List.of(storeA.getId()), ClearanceItemStatus.OPEN);

    assertThat(result).isEmpty();
  }

  @Test
  void 최대할인율_계산_정확() {
    // 할인율: (1000-700)/1000 = 30%, (2000-1200)/2000 = 40%
    saveItem(storeA, "1000", "700", tomorrowAt(15, 0), ClearanceItemStatus.OPEN);
    saveItem(storeA, "2000", "1200", tomorrowAt(16, 0), ClearanceItemStatus.OPEN);
    clearanceItemRepository.flush();

    List<Object[]> result =
        clearanceItemRepository.findActiveDealSummaryByStoreIds(
            List.of(storeA.getId()), ClearanceItemStatus.OPEN);

    assertThat(result).hasSize(1);
    Object[] row = result.get(0);
    // MAX discount rate = 0.40
    double maxDiscountRate = ((Number) row[2]).doubleValue();
    assertThat(maxDiscountRate).isCloseTo(0.40, within(0.01));
  }

  @Test
  void 가장_빠른_픽업마감시간_반환() {
    LocalDateTime earlier = tomorrowAt(14, 0);
    LocalDateTime later = tomorrowAt(20, 0);
    saveItem(storeA, "1000", "700", later, ClearanceItemStatus.OPEN);
    saveItem(storeA, "1000", "700", earlier, ClearanceItemStatus.OPEN);
    clearanceItemRepository.flush();

    List<Object[]> result =
        clearanceItemRepository.findActiveDealSummaryByStoreIds(
            List.of(storeA.getId()), ClearanceItemStatus.OPEN);

    assertThat(result).hasSize(1);
    LocalDateTime nearestEnd = (LocalDateTime) result.get(0)[3];
    assertThat(nearestEnd).isEqualTo(earlier);
  }

  @Test
  void 여러_매장_배치_집계() {
    saveItem(storeA, "1000", "700", tomorrowAt(15, 0), ClearanceItemStatus.OPEN);
    saveItem(storeA, "1000", "700", tomorrowAt(16, 0), ClearanceItemStatus.OPEN);
    saveItem(storeB, "2000", "1000", tomorrowAt(18, 0), ClearanceItemStatus.OPEN);
    clearanceItemRepository.flush();

    List<Object[]> result =
        clearanceItemRepository.findActiveDealSummaryByStoreIds(
            List.of(storeA.getId(), storeB.getId()), ClearanceItemStatus.OPEN);

    assertThat(result).hasSize(2);
  }

  // ── helper ───────────────────────────────────────────────────────────────────────────────────

  private Store saveStore(Seller seller, String name) {
    return storeRepository.save(
        Store.builder()
            .seller(seller)
            .businessNumber("1234567890")
            .name(name)
            .roadAddress("서울시 중구 테스트로 1")
            .zonecode("04524")
            .location(GeometryUtil.toPoint(37.5665, 126.9780))
            .phone("02-1234-5678")
            .operationStatus(OperationStatus.OPEN)
            .build());
  }

  private void saveItem(
      Store store,
      String regularPrice,
      String salePrice,
      LocalDateTime pickupEndAt,
      ClearanceItemStatus status) {
    ClearanceItem item =
        ClearanceItem.builder()
            .store(store)
            .name("떨이상품")
            .regularPrice(new BigDecimal(regularPrice))
            .salePrice(new BigDecimal(salePrice))
            .totalQuantity(5)
            .pickupStartAt(LocalDate.now().atTime(9, 0))
            .pickupEndAt(pickupEndAt)
            .build();
    // builder 로는 OPEN 상태로 생성됨 — CLOSED 로 바꾸려면 close() 호출
    if (status == ClearanceItemStatus.CLOSED) {
      item.close();
    }
    clearanceItemRepository.save(item);
  }

  private static LocalDateTime tomorrowAt(int hour, int minute) {
    return LocalDate.now().plusDays(1).atTime(hour, minute);
  }
}
