package com.magampick.search.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.magampick.TestcontainersConfiguration;
import com.magampick.global.common.GeometryUtil;
import com.magampick.global.config.JpaAuditingConfig;
import com.magampick.seller.domain.Seller;
import com.magampick.seller.repository.SellerRepository;
import com.magampick.store.domain.OperationStatus;
import com.magampick.store.domain.Store;
import com.magampick.store.domain.StoreBusinessHour;
import com.magampick.store.repository.StoreBusinessHourRepository;
import com.magampick.store.repository.StoreNameSuggestion;
import com.magampick.store.repository.StoreRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Phase 9 검색: StoreRepository 신규 쿼리 테스트. findStoreIdsWithin5kmMatchingName /
 * suggestStoreNamesWithin5km
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
class SearchStoreRepositoryTest {

  // origin: 서울시청
  private static final double ORIGIN_LAT = 37.5665;
  private static final double ORIGIN_LNG = 126.9780;
  // 5km 이내 (~280m)
  private static final double NEAR_LAT = 37.5685;
  private static final double NEAR_LNG = 126.9800;
  // 5km 초과 (~8.7km)
  private static final double FAR_LAT = 37.6200;
  private static final double FAR_LNG = 127.0500;

  @Autowired StoreRepository storeRepository;
  @Autowired StoreBusinessHourRepository storeBusinessHourRepository;
  @Autowired SellerRepository sellerRepository;

  private Seller seller;
  private String today;

  @BeforeEach
  void setUp() {
    seller =
        sellerRepository.save(
            Seller.builder()
                .email("seller_search_" + System.nanoTime() + "@test.com")
                .passwordHash("x")
                .ownerName("테스트사장")
                .build());
    today = LocalDate.now().getDayOfWeek().name();
  }

  // ── findStoreIdsWithin5kmMatchingName ───────────────────────────────────────────────────────

  @Test
  void 매장명_부분일치_5km이내_OPEN_오늘영업_반환() {
    Store store = saveOpenStoreNear("빵집할인마트");
    saveBusinessHour(store, LocalDate.now().getDayOfWeek());
    storeRepository.flush();
    storeBusinessHourRepository.flush();

    List<Long> result =
        storeRepository.findStoreIdsWithin5kmMatchingName(ORIGIN_LAT, ORIGIN_LNG, today, "빵집");

    assertThat(result).containsExactly(store.getId());
  }

  @Test
  void 매장명_불일치는_제외() {
    Store store = saveOpenStoreNear("완전다른이름");
    saveBusinessHour(store, LocalDate.now().getDayOfWeek());
    storeRepository.flush();
    storeBusinessHourRepository.flush();

    List<Long> result =
        storeRepository.findStoreIdsWithin5kmMatchingName(ORIGIN_LAT, ORIGIN_LNG, today, "빵집");

    assertThat(result).isEmpty();
  }

  @Test
  void 매장명_일치해도_5km_초과면_제외() {
    Store store = saveOpenStoreFar("빵집멀리");
    saveBusinessHour(store, LocalDate.now().getDayOfWeek());
    storeRepository.flush();
    storeBusinessHourRepository.flush();

    List<Long> result =
        storeRepository.findStoreIdsWithin5kmMatchingName(ORIGIN_LAT, ORIGIN_LNG, today, "빵집");

    assertThat(result).isEmpty();
  }

  @Test
  void 매장명_일치해도_OPEN_아니면_제외() {
    Store store = saveBreakStoreNear("빵집휴식중");
    saveBusinessHour(store, LocalDate.now().getDayOfWeek());
    storeRepository.flush();
    storeBusinessHourRepository.flush();

    List<Long> result =
        storeRepository.findStoreIdsWithin5kmMatchingName(ORIGIN_LAT, ORIGIN_LNG, today, "빵집");

    assertThat(result).isEmpty();
  }

  @Test
  void 매장명_일치해도_오늘영업_없으면_제외() {
    Store store = saveOpenStoreNear("빵집오늘휴무");
    // 영업시간 없음
    storeRepository.flush();

    List<Long> result =
        storeRepository.findStoreIdsWithin5kmMatchingName(ORIGIN_LAT, ORIGIN_LNG, today, "빵집");

    assertThat(result).isEmpty();
  }

  @Test
  void ESCAPE_절_리터럴_퍼센트_매칭() {
    // "50%빵집" 은 이름에 '%' 가 포함됨. 이스케이프된 "\\%" 로 검색하면 이 매장만 리턴되어야 함.
    // "크루아상" 은 '%' 가 없으므로 제외돼야 함.
    Store croissantStore = saveOpenStoreNear("크루아상");
    Store percentStore = saveOpenStoreNear("50%빵집");
    saveBusinessHour(croissantStore, LocalDate.now().getDayOfWeek());
    saveBusinessHour(percentStore, LocalDate.now().getDayOfWeek());
    storeRepository.flush();
    storeBusinessHourRepository.flush();

    List<Long> result =
        storeRepository.findStoreIdsWithin5kmMatchingName(ORIGIN_LAT, ORIGIN_LNG, today, "\\%");

    assertThat(result).containsExactly(percentStore.getId());
    assertThat(result).doesNotContain(croissantStore.getId());
  }

  @Test
  void 대소문자_무관_매칭_ILIKE() {
    Store store = saveOpenStoreNear("CafeHello");
    saveBusinessHour(store, LocalDate.now().getDayOfWeek());
    storeRepository.flush();
    storeBusinessHourRepository.flush();

    // 소문자로 검색
    List<Long> result =
        storeRepository.findStoreIdsWithin5kmMatchingName(ORIGIN_LAT, ORIGIN_LNG, today, "cafe");

    assertThat(result).containsExactly(store.getId());
  }

  // ── suggestStoreNamesWithin5km ──────────────────────────────────────────────────────────────

  @Test
  void 자동완성_word_similarity_threshold_이상_반환() {
    Store store = saveOpenStoreNear("빵집");
    saveBusinessHour(store, LocalDate.now().getDayOfWeek());
    storeRepository.flush();
    storeBusinessHourRepository.flush();

    // threshold 0.3, q="빵" → word_similarity("빵", "빵집") 는 0.3 이상일 것
    List<StoreNameSuggestion> result =
        storeRepository.suggestStoreNamesWithin5km(ORIGIN_LAT, ORIGIN_LNG, today, "빵", 0.3);

    assertThat(result).isNotEmpty();
    assertThat(result.get(0).getName()).isEqualTo("빵집");
    assertThat(result.get(0).getSimilarity()).isGreaterThanOrEqualTo(0.3);
  }

  @Test
  void 자동완성_유사도_낮은_경우_제외() {
    Store store = saveOpenStoreNear("완전다른이름의매장");
    saveBusinessHour(store, LocalDate.now().getDayOfWeek());
    storeRepository.flush();
    storeBusinessHourRepository.flush();

    // threshold 0.3, q="빵" 은 "완전다른이름의매장" 과 유사도 매우 낮음
    List<StoreNameSuggestion> result =
        storeRepository.suggestStoreNamesWithin5km(ORIGIN_LAT, ORIGIN_LNG, today, "빵", 0.3);

    assertThat(result).isEmpty();
  }

  @Test
  void 자동완성_5km_초과_매장_제외() {
    Store farStore = saveOpenStoreFar("빵집멀리");
    saveBusinessHour(farStore, LocalDate.now().getDayOfWeek());
    storeRepository.flush();
    storeBusinessHourRepository.flush();

    List<StoreNameSuggestion> result =
        storeRepository.suggestStoreNamesWithin5km(ORIGIN_LAT, ORIGIN_LNG, today, "빵집", 0.3);

    assertThat(result).isEmpty();
  }

  @Test
  void 자동완성_유사도_높은_순으로_정렬() {
    Store exact = saveOpenStoreNear("빵집");
    Store partial = saveOpenStoreNear("빵집과자");
    saveBusinessHour(exact, LocalDate.now().getDayOfWeek());
    saveBusinessHour(partial, LocalDate.now().getDayOfWeek());
    storeRepository.flush();
    storeBusinessHourRepository.flush();

    List<StoreNameSuggestion> result =
        storeRepository.suggestStoreNamesWithin5km(ORIGIN_LAT, ORIGIN_LNG, today, "빵집", 0.3);

    assertThat(result).hasSizeGreaterThanOrEqualTo(2);
    // 첫 번째가 두 번째보다 유사도가 높거나 같아야 함
    assertThat(result.get(0).getSimilarity())
        .isGreaterThanOrEqualTo(result.get(result.size() - 1).getSimilarity());
  }

  // ── helper ───────────────────────────────────────────────────────────────────────────────────

  private Store saveOpenStoreNear(String name) {
    return storeRepository.save(
        Store.builder()
            .seller(seller)
            .businessNumber("1234567890")
            .representativeName("홍길동")
            .openDate(LocalDate.of(2024, 3, 15))
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
            .representativeName("홍길동")
            .openDate(LocalDate.of(2024, 3, 15))
            .name(name)
            .roadAddress("서울시 노원구 1")
            .zonecode("01234")
            .location(GeometryUtil.toPoint(FAR_LAT, FAR_LNG))
            .phone("02-0000-0000")
            .operationStatus(OperationStatus.OPEN)
            .build());
  }

  private Store saveBreakStoreNear(String name) {
    return storeRepository.save(
        Store.builder()
            .seller(seller)
            .businessNumber("1234567890")
            .representativeName("홍길동")
            .openDate(LocalDate.of(2024, 3, 15))
            .name(name)
            .roadAddress("서울시 중구 1")
            .zonecode("04524")
            .location(GeometryUtil.toPoint(NEAR_LAT, NEAR_LNG))
            .phone("02-0000-0000")
            .operationStatus(OperationStatus.BREAK)
            .build());
  }

  private void saveBusinessHour(Store store, DayOfWeek day) {
    storeBusinessHourRepository.save(
        StoreBusinessHour.builder()
            .store(store)
            .dayOfWeek(day)
            .openTime(LocalTime.of(9, 0))
            .closeTime(LocalTime.of(21, 0))
            .build());
  }
}
