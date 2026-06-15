package com.magampick.search.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.magampick.TestcontainersConfiguration;
import com.magampick.clearance.domain.ClearanceItem;
import com.magampick.clearance.repository.ClearanceItemRepository;
import com.magampick.clearance.repository.DealNameSuggestion;
import com.magampick.clearance.repository.DealSearchCandidate;
import com.magampick.global.common.GeometryUtil;
import com.magampick.global.config.JpaAuditingConfig;
import com.magampick.seller.domain.Seller;
import com.magampick.seller.repository.SellerRepository;
import com.magampick.store.domain.OperationStatus;
import com.magampick.store.domain.Store;
import com.magampick.store.domain.StoreBusinessHour;
import com.magampick.store.repository.StoreBusinessHourRepository;
import com.magampick.store.repository.StoreRepository;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Phase 9 검색: ClearanceItemRepository 신규 쿼리 테스트. searchOpenDealsByStoreIds /
 * suggestDealNamesByStoreIds
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
class SearchDealRepositoryTest {

  @Autowired ClearanceItemRepository clearanceItemRepository;
  @Autowired StoreRepository storeRepository;
  @Autowired StoreBusinessHourRepository storeBusinessHourRepository;
  @Autowired SellerRepository sellerRepository;

  private Seller seller;
  private LocalDateTime now;

  @BeforeEach
  void setUp() {
    seller =
        sellerRepository.save(
            Seller.builder()
                .email("seller_deal_" + System.nanoTime() + "@test.com")
                .passwordHash("x")
                .ownerName("테스트사장")
                .build());
    now = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
  }

  // ── searchOpenDealsByStoreIds ─────────────────────────────────────────────────────────────────

  @Test
  void 떨이명_부분일치_OPEN_반환() {
    Store store = saveOpenStoreNear("매장");
    ClearanceItem item = saveItem(store, "크로아상할인", "5000", "3500");
    clearanceItemRepository.flush();

    List<DealSearchCandidate> result =
        clearanceItemRepository.searchOpenDealsByStoreIds(List.of(store.getId()), "크로아상");

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getId()).isEqualTo(item.getId());
    assertThat(result.get(0).getName()).isEqualTo("크로아상할인");
    assertThat(result.get(0).getStoreId()).isEqualTo(store.getId());
  }

  @Test
  void 떨이명_불일치_제외() {
    Store store = saveOpenStoreNear("매장");
    saveItem(store, "완전다른이름", "5000", "3500");
    clearanceItemRepository.flush();

    List<DealSearchCandidate> result =
        clearanceItemRepository.searchOpenDealsByStoreIds(List.of(store.getId()), "크로아상");

    assertThat(result).isEmpty();
  }

  @Test
  void storeIds_외부_매장_제외() {
    Store store1 = saveOpenStoreNear("매장1");
    Store store2 = saveOpenStoreNear("매장2");
    saveItem(store2, "크로아상", "5000", "3500");
    clearanceItemRepository.flush();

    // store2 는 storeIds 에 포함 안됨
    List<DealSearchCandidate> result =
        clearanceItemRepository.searchOpenDealsByStoreIds(List.of(store1.getId()), "크로아상");

    assertThat(result).isEmpty();
  }

  @Test
  void OPEN이_아닌_떨이는_제외() {
    Store store = saveOpenStoreNear("매장");
    ClearanceItem item = saveItem(store, "크로아상", "5000", "3500");
    item.close();
    clearanceItemRepository.save(item);
    clearanceItemRepository.flush();

    List<DealSearchCandidate> result =
        clearanceItemRepository.searchOpenDealsByStoreIds(List.of(store.getId()), "크로아상");

    assertThat(result).isEmpty();
  }

  @Test
  void 응답_필드_정확히_반환() {
    Store store = saveOpenStoreNear("빵집");
    ClearanceItem item = saveItem(store, "단팥빵특가", "3000", "2000");
    clearanceItemRepository.flush();

    List<DealSearchCandidate> result =
        clearanceItemRepository.searchOpenDealsByStoreIds(List.of(store.getId()), "단팥빵");

    assertThat(result).hasSize(1);
    DealSearchCandidate c = result.get(0);
    assertThat(c.getStoreId()).isEqualTo(store.getId());
    assertThat(c.getName()).isEqualTo("단팥빵특가");
    assertThat(c.getRegularPrice()).isEqualByComparingTo(new BigDecimal("3000"));
    assertThat(c.getSalePrice()).isEqualByComparingTo(new BigDecimal("2000"));
    assertThat(c.getImageUrl()).isNull(); // product 없음
  }

  // ── suggestDealNamesByStoreIds ────────────────────────────────────────────────────────────────

  @Test
  void 떨이_자동완성_word_similarity_threshold_이상_반환() {
    Store store = saveOpenStoreNear("매장");
    saveItem(store, "크로아상", "5000", "3500");
    clearanceItemRepository.flush();

    List<DealNameSuggestion> result =
        clearanceItemRepository.suggestDealNamesByStoreIds(List.of(store.getId()), "크로", 0.3);

    assertThat(result).isNotEmpty();
    assertThat(result.get(0).getName()).isEqualTo("크로아상");
    assertThat(result.get(0).getSimilarity()).isGreaterThanOrEqualTo(0.3);
  }

  @Test
  void 떨이_자동완성_유사도_낮은_경우_제외() {
    Store store = saveOpenStoreNear("매장");
    saveItem(store, "완전다른이름", "5000", "3500");
    clearanceItemRepository.flush();

    List<DealNameSuggestion> result =
        clearanceItemRepository.suggestDealNamesByStoreIds(List.of(store.getId()), "크로아상", 0.3);

    assertThat(result).isEmpty();
  }

  @Test
  void 떨이_자동완성_storeIds_외부_매장_제외() {
    Store store1 = saveOpenStoreNear("매장1");
    Store store2 = saveOpenStoreNear("매장2");
    saveItem(store2, "크로아상", "5000", "3500");
    clearanceItemRepository.flush();

    List<DealNameSuggestion> result =
        clearanceItemRepository.suggestDealNamesByStoreIds(List.of(store1.getId()), "크로아상", 0.3);

    assertThat(result).isEmpty();
  }

  // ── helper ───────────────────────────────────────────────────────────────────────────────────

  private Store saveOpenStoreNear(String name) {
    Store store =
        storeRepository.save(
            Store.builder()
                .seller(seller)
                .businessNumber("1234567890")
                .representativeName("홍길동")
                .openDate(LocalDate.of(2024, 3, 15))
                .name(name)
                .roadAddress("서울시 중구 1")
                .zonecode("04524")
                .location(GeometryUtil.toPoint(37.5685, 126.9800))
                .phone("02-0000-0000")
                .operationStatus(OperationStatus.OPEN)
                .build());
    storeBusinessHourRepository.save(
        StoreBusinessHour.builder()
            .store(store)
            .dayOfWeek(DayOfWeek.of(LocalDate.now().getDayOfWeek().getValue()))
            .openTime(LocalTime.of(9, 0))
            .closeTime(LocalTime.of(21, 0))
            .build());
    return store;
  }

  private ClearanceItem saveItem(Store store, String name, String regular, String sale) {
    return clearanceItemRepository.save(
        ClearanceItem.builder()
            .store(store)
            .name(name)
            .regularPrice(new BigDecimal(regular))
            .salePrice(new BigDecimal(sale))
            .totalQuantity(5)
            .pickupStartAt(now.minusHours(1))
            .pickupEndAt(now.plusHours(2))
            .build());
  }
}
