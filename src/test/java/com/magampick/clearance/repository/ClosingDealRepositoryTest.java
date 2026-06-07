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
import com.magampick.store.domain.StoreBusinessHour;
import com.magampick.store.repository.StoreBusinessHourRepository;
import com.magampick.store.repository.StoreRepository;
import java.math.BigDecimal;
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
 * ClosingDeal 네이티브 쿼리 테스트. 경계 조건: 60분 윈도우(이내 포함/초과 제외), 5km, OPEN+오늘 영업, status=OPEN, LIMIT5 + 마감순
 * 정렬.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
class ClosingDealRepositoryTest {

  // origin: 서울시청
  private static final double ORIGIN_LAT = 37.5665;
  private static final double ORIGIN_LNG = 126.9780;
  // 5km 이내 (~280m)
  private static final double NEAR_LAT = 37.5685;
  private static final double NEAR_LNG = 126.9800;
  // 5km 초과 (~8.7km)
  private static final double FAR_LAT = 37.6200;
  private static final double FAR_LNG = 127.0500;

  @Autowired ClearanceItemRepository clearanceItemRepository;
  @Autowired StoreRepository storeRepository;
  @Autowired StoreBusinessHourRepository storeBusinessHourRepository;
  @Autowired SellerRepository sellerRepository;

  private Seller seller;
  private String today;
  private LocalDateTime now;
  private LocalDateTime until;

  @BeforeEach
  void setUp() {
    seller =
        sellerRepository.save(
            Seller.builder()
                .email("seller_" + System.nanoTime() + "@test.com")
                .passwordHash("x")
                .ownerName("테스트사장")
                .build());
    today = LocalDate.now().getDayOfWeek().name();
    // PostgreSQL TIMESTAMP = 마이크로초 정밀도 → 나노초 잘림 방지
    now = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
    until = now.plusMinutes(60);
  }

  // ── 60분 윈도우 경계 ─────────────────────────────────────────────────────────────────────────────

  @Test
  void 픽업마감이_60분이내이면_포함() {
    Store store = saveOpenStoreNear("근처매장");
    saveBusinessHour(store, LocalDate.now().getDayOfWeek());
    saveItem(store, "테스트떨이", "5000", "3500", now.plusMinutes(30), ClearanceItemStatus.OPEN);
    clearanceItemRepository.flush();

    List<ClosingDealCandidate> result =
        clearanceItemRepository.findClosingSoonDeals(ORIGIN_LAT, ORIGIN_LNG, today, now, until);

    assertThat(result).hasSize(1);
  }

  @Test
  void 픽업마감이_정확히_now이면_포함() {
    Store store = saveOpenStoreNear("근처매장");
    saveBusinessHour(store, LocalDate.now().getDayOfWeek());
    saveItem(store, "마감직전", "5000", "3500", now, ClearanceItemStatus.OPEN);
    clearanceItemRepository.flush();

    List<ClosingDealCandidate> result =
        clearanceItemRepository.findClosingSoonDeals(ORIGIN_LAT, ORIGIN_LNG, today, now, until);

    assertThat(result).hasSize(1);
  }

  @Test
  void 픽업마감이_60분_초과이면_제외() {
    Store store = saveOpenStoreNear("근처매장");
    saveBusinessHour(store, LocalDate.now().getDayOfWeek());
    // now + 61분 → until(now+60) 초과
    saveItem(store, "늦은떨이", "5000", "3500", now.plusMinutes(61), ClearanceItemStatus.OPEN);
    clearanceItemRepository.flush();

    List<ClosingDealCandidate> result =
        clearanceItemRepository.findClosingSoonDeals(ORIGIN_LAT, ORIGIN_LNG, today, now, until);

    assertThat(result).isEmpty();
  }

  // ── 5km 경계 ──────────────────────────────────────────────────────────────────────────────────

  @Test
  void 반경_5km_초과_매장의_떨이는_제외() {
    Store farStore = saveOpenStoreFar("먼매장");
    saveBusinessHour(farStore, LocalDate.now().getDayOfWeek());
    saveItem(farStore, "먼떨이", "5000", "3500", now.plusMinutes(30), ClearanceItemStatus.OPEN);
    clearanceItemRepository.flush();

    List<ClosingDealCandidate> result =
        clearanceItemRepository.findClosingSoonDeals(ORIGIN_LAT, ORIGIN_LNG, today, now, until);

    assertThat(result).isEmpty();
  }

  // ── 매장 조건: OPEN + 오늘 영업 ──────────────────────────────────────────────────────────────────

  @Test
  void 매장_OPEN이_아니면_제외() {
    Store closedStore =
        storeRepository.save(
            Store.builder()
                .seller(seller)
                .businessNumber("1234567890")
                .name("휴식매장")
                .roadAddress("서울시 중구 1")
                .zonecode("04524")
                .location(GeometryUtil.toPoint(NEAR_LAT, NEAR_LNG))
                .phone("02-0000-0000")
                .operationStatus(OperationStatus.BREAK)
                .build());
    saveBusinessHour(closedStore, LocalDate.now().getDayOfWeek());
    saveItem(closedStore, "떨이", "5000", "3500", now.plusMinutes(30), ClearanceItemStatus.OPEN);
    clearanceItemRepository.flush();

    List<ClosingDealCandidate> result =
        clearanceItemRepository.findClosingSoonDeals(ORIGIN_LAT, ORIGIN_LNG, today, now, until);

    assertThat(result).isEmpty();
  }

  @Test
  void 오늘_영업시간_없는_매장의_떨이는_제외() {
    Store store = saveOpenStoreNear("오늘휴무");
    // 영업시간 없음 → EXISTS 서브쿼리 false
    saveItem(store, "떨이", "5000", "3500", now.plusMinutes(30), ClearanceItemStatus.OPEN);
    clearanceItemRepository.flush();

    List<ClosingDealCandidate> result =
        clearanceItemRepository.findClosingSoonDeals(ORIGIN_LAT, ORIGIN_LNG, today, now, until);

    assertThat(result).isEmpty();
  }

  // ── status = OPEN 만 ──────────────────────────────────────────────────────────────────────────

  @Test
  void status_CLOSED_떨이는_제외() {
    Store store = saveOpenStoreNear("근처매장");
    saveBusinessHour(store, LocalDate.now().getDayOfWeek());
    ClearanceItem item = buildItem(store, null, "5000", "3500", now.plusMinutes(30));
    item.close(); // CLOSED 로 변경
    clearanceItemRepository.save(item);
    clearanceItemRepository.flush();

    List<ClosingDealCandidate> result =
        clearanceItemRepository.findClosingSoonDeals(ORIGIN_LAT, ORIGIN_LNG, today, now, until);

    assertThat(result).isEmpty();
  }

  // ── LIMIT 5 + 마감순 정렬 ─────────────────────────────────────────────────────────────────────

  @Test
  void LIMIT5_초과시_5개만_반환() {
    Store store = saveOpenStoreNear("근처매장");
    saveBusinessHour(store, LocalDate.now().getDayOfWeek());
    for (int i = 1; i <= 6; i++) {
      saveItem(store, "떨이" + i, "5000", "3500", now.plusMinutes(i * 5), ClearanceItemStatus.OPEN);
    }
    clearanceItemRepository.flush();

    List<ClosingDealCandidate> result =
        clearanceItemRepository.findClosingSoonDeals(ORIGIN_LAT, ORIGIN_LNG, today, now, until);

    assertThat(result).hasSize(5);
  }

  @Test
  void 마감순_픽업마감시간_ASC_정렬() {
    Store store = saveOpenStoreNear("근처매장");
    saveBusinessHour(store, LocalDate.now().getDayOfWeek());
    // 나중에 저장한 순서와 반대로 마감이 빠름을 확인
    saveItem(store, "늦게마감", "5000", "3500", now.plusMinutes(55), ClearanceItemStatus.OPEN);
    saveItem(store, "빨리마감", "5000", "3500", now.plusMinutes(10), ClearanceItemStatus.OPEN);
    clearanceItemRepository.flush();

    List<ClosingDealCandidate> result =
        clearanceItemRepository.findClosingSoonDeals(ORIGIN_LAT, ORIGIN_LNG, today, now, until);

    assertThat(result).hasSize(2);
    // 빨리 마감하는 상품이 먼저
    assertThat(result.get(0).getPickupDeadline()).isBefore(result.get(1).getPickupDeadline());
  }

  // ── projection 필드 반환 ──────────────────────────────────────────────────────────────────────

  @Test
  void 응답_필드_반환_정확() {
    Store store = saveOpenStoreNear("우리빵집");
    saveBusinessHour(store, LocalDate.now().getDayOfWeek());
    LocalDateTime deadline = now.plusMinutes(45);
    saveItem(store, "크로아상", "4500", "3000", deadline, ClearanceItemStatus.OPEN);
    clearanceItemRepository.flush();

    List<ClosingDealCandidate> result =
        clearanceItemRepository.findClosingSoonDeals(ORIGIN_LAT, ORIGIN_LNG, today, now, until);

    assertThat(result).hasSize(1);
    ClosingDealCandidate c = result.get(0);
    assertThat(c.getStoreName()).isEqualTo("우리빵집");
    assertThat(c.getProductName()).isEqualTo("크로아상");
    assertThat(c.getImageUrl()).isNull(); // product 없음
    assertThat(c.getRegularPrice()).isEqualByComparingTo(new BigDecimal("4500"));
    assertThat(c.getSalePrice()).isEqualByComparingTo(new BigDecimal("3000"));
    assertThat(c.getPickupDeadline()).isEqualTo(deadline);
  }

  // ── helper ───────────────────────────────────────────────────────────────────────────────────

  private Store saveOpenStoreNear(String name) {
    return storeRepository.save(
        Store.builder()
            .seller(seller)
            .businessNumber("1234567890")
            .name(name)
            .roadAddress("서울시 중구 1")
            .zonecode("04524")
            .location(GeometryUtil.toPoint(NEAR_LAT, NEAR_LNG))
            .phone("02-0000-0000")
            .operationStatus(OperationStatus.OPEN)
            .build());
  }

  private Store saveOpenStoreFar(String name) {
    return storeRepository.save(
        Store.builder()
            .seller(seller)
            .businessNumber("1234567890")
            .name(name)
            .roadAddress("서울시 노원구 1")
            .zonecode("01234")
            .location(GeometryUtil.toPoint(FAR_LAT, FAR_LNG))
            .phone("02-0000-0000")
            .operationStatus(OperationStatus.OPEN)
            .build());
  }

  private void saveBusinessHour(Store store, java.time.DayOfWeek day) {
    storeBusinessHourRepository.save(
        StoreBusinessHour.builder()
            .store(store)
            .dayOfWeek(day)
            .openTime(LocalTime.of(9, 0))
            .closeTime(LocalTime.of(21, 0))
            .build());
  }

  private void saveItem(
      Store store,
      String name,
      String regular,
      String sale,
      LocalDateTime pickupEndAt,
      ClearanceItemStatus status) {
    ClearanceItem item = buildItem(store, name, regular, sale, pickupEndAt);
    if (status == ClearanceItemStatus.CLOSED) {
      item.close();
    }
    clearanceItemRepository.save(item);
  }

  private ClearanceItem buildItem(
      Store store, String name, String regular, String sale, LocalDateTime pickupEndAt) {
    return ClearanceItem.builder()
        .store(store)
        .name(name != null ? name : "떨이상품")
        .regularPrice(new BigDecimal(regular))
        .salePrice(new BigDecimal(sale))
        .totalQuantity(5)
        .pickupStartAt(now.minusHours(1))
        .pickupEndAt(pickupEndAt)
        .build();
  }
}
