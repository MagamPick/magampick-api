package com.magampick.store.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.magampick.TestcontainersConfiguration;
import com.magampick.global.common.GeometryUtil;
import com.magampick.global.config.JpaAuditingConfig;
import com.magampick.seller.domain.Seller;
import com.magampick.seller.repository.SellerRepository;
import com.magampick.store.domain.OperationStatus;
import com.magampick.store.domain.Store;
import com.magampick.store.domain.StoreBusinessHour;
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

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
class StoreMapRepositoryTest {

  // origin: 서울시청 인근
  private static final double ORIGIN_LAT = 37.5665;
  private static final double ORIGIN_LNG = 126.9780;

  // 약 280m (1km 반경 이내)
  private static final double NEAR_280M_LAT = 37.5685;
  private static final double NEAR_280M_LNG = 126.9800;

  // 약 1.5km (1km 초과, 3km 이내)
  private static final double MID_1_5KM_LAT = 37.5530;
  private static final double MID_1_5KM_LNG = 126.9780;

  // 약 8.7km (5km 초과)
  private static final double FAR_LAT = 37.6200;
  private static final double FAR_LNG = 127.0500;

  @Autowired StoreRepository storeRepository;
  @Autowired StoreBusinessHourRepository storeBusinessHourRepository;
  @Autowired SellerRepository sellerRepository;

  private Seller savedSeller;
  private String today;

  @BeforeEach
  void setUp() {
    savedSeller =
        sellerRepository.save(
            Seller.builder()
                .email("seller_" + System.nanoTime() + "@test.com")
                .passwordHash("x")
                .ownerName("테스트사장")
                .build());
    today = LocalDate.now().getDayOfWeek().name();
  }

  // ── 반경 1km 경계 테스트 ─────────────────────────────────────────────────────────────────────

  @Test
  void 반경1km_이내_매장_반환() {
    Store store = saveStore("1km이내", NEAR_280M_LAT, NEAR_280M_LNG, OperationStatus.OPEN);
    saveBusinessHour(store, LocalDate.now().getDayOfWeek());
    storeRepository.flush();
    storeBusinessHourRepository.flush();

    List<MapStoreCandidate> result =
        storeRepository.findMapStoresWithinRadius(ORIGIN_LAT, ORIGIN_LNG, 1000, today);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getId()).isEqualTo(store.getId());
  }

  @Test
  void 반경1km_초과_매장은_1km_쿼리에서_제외() {
    Store store = saveStore("1.5km", MID_1_5KM_LAT, MID_1_5KM_LNG, OperationStatus.OPEN);
    saveBusinessHour(store, LocalDate.now().getDayOfWeek());
    storeRepository.flush();
    storeBusinessHourRepository.flush();

    List<MapStoreCandidate> result =
        storeRepository.findMapStoresWithinRadius(ORIGIN_LAT, ORIGIN_LNG, 1000, today);

    assertThat(result).isEmpty();
  }

  // ── 반경 3km 경계 테스트 ─────────────────────────────────────────────────────────────────────

  @Test
  void 반경3km_이내_매장_반환() {
    Store nearStore = saveStore("280m", NEAR_280M_LAT, NEAR_280M_LNG, OperationStatus.OPEN);
    Store midStore = saveStore("1.5km", MID_1_5KM_LAT, MID_1_5KM_LNG, OperationStatus.OPEN);
    saveBusinessHour(nearStore, LocalDate.now().getDayOfWeek());
    saveBusinessHour(midStore, LocalDate.now().getDayOfWeek());
    storeRepository.flush();
    storeBusinessHourRepository.flush();

    List<MapStoreCandidate> result =
        storeRepository.findMapStoresWithinRadius(ORIGIN_LAT, ORIGIN_LNG, 3000, today);

    assertThat(result).hasSize(2);
  }

  // ── 반경 5km 경계 테스트 ─────────────────────────────────────────────────────────────────────

  @Test
  void 반경5km_초과_매장은_제외() {
    Store store = saveStore("8.7km", FAR_LAT, FAR_LNG, OperationStatus.OPEN);
    saveBusinessHour(store, LocalDate.now().getDayOfWeek());
    storeRepository.flush();
    storeBusinessHourRepository.flush();

    List<MapStoreCandidate> result =
        storeRepository.findMapStoresWithinRadius(ORIGIN_LAT, ORIGIN_LNG, 5000, today);

    assertThat(result).isEmpty();
  }

  // ── OPEN + 오늘 영업요일 필터 ─────────────────────────────────────────────────────────────────

  @Test
  void OPEN이_아닌_매장은_제외() {
    Store store = saveStore("BREAK매장", NEAR_280M_LAT, NEAR_280M_LNG, OperationStatus.BREAK);
    saveBusinessHour(store, LocalDate.now().getDayOfWeek());
    storeRepository.flush();
    storeBusinessHourRepository.flush();

    List<MapStoreCandidate> result =
        storeRepository.findMapStoresWithinRadius(ORIGIN_LAT, ORIGIN_LNG, 3000, today);

    assertThat(result).isEmpty();
  }

  @Test
  void 오늘_영업시간_없는_매장은_제외() {
    saveStore("오늘미영업", NEAR_280M_LAT, NEAR_280M_LNG, OperationStatus.OPEN);
    // 영업시간 미등록
    storeRepository.flush();

    List<MapStoreCandidate> result =
        storeRepository.findMapStoresWithinRadius(ORIGIN_LAT, ORIGIN_LNG, 3000, today);

    assertThat(result).isEmpty();
  }

  // ── lat/lng projection 정확도 ────────────────────────────────────────────────────────────────

  @Test
  void lat_lng_projection_값_정확() {
    Store store = saveStore("위경도확인", NEAR_280M_LAT, NEAR_280M_LNG, OperationStatus.OPEN);
    saveBusinessHour(store, LocalDate.now().getDayOfWeek());
    storeRepository.flush();
    storeBusinessHourRepository.flush();

    List<MapStoreCandidate> result =
        storeRepository.findMapStoresWithinRadius(ORIGIN_LAT, ORIGIN_LNG, 3000, today);

    assertThat(result).hasSize(1);
    MapStoreCandidate c = result.get(0);
    assertThat(c.getLatitude()).isCloseTo(NEAR_280M_LAT, within(0.001));
    assertThat(c.getLongitude()).isCloseTo(NEAR_280M_LNG, within(0.001));
  }

  @Test
  void distanceMeters_미터단위_반환() {
    Store store = saveStore("거리확인", NEAR_280M_LAT, NEAR_280M_LNG, OperationStatus.OPEN);
    saveBusinessHour(store, LocalDate.now().getDayOfWeek());
    storeRepository.flush();
    storeBusinessHourRepository.flush();

    List<MapStoreCandidate> result =
        storeRepository.findMapStoresWithinRadius(ORIGIN_LAT, ORIGIN_LNG, 3000, today);

    assertThat(result).hasSize(1);
    // 약 280m, 300m tolerance
    assertThat(result.get(0).getDistanceMeters()).isCloseTo(280.0, within(300.0));
  }

  // ── helper ───────────────────────────────────────────────────────────────────────────────────

  private Store saveStore(String name, double lat, double lng, OperationStatus status) {
    return storeRepository.save(
        Store.builder()
            .seller(savedSeller)
            .businessNumber("1234567890")
            .name(name)
            .roadAddress("서울시 중구 테스트로 1")
            .zonecode("04524")
            .location(GeometryUtil.toPoint(lat, lng))
            .phone("02-1234-5678")
            .operationStatus(status)
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
}
