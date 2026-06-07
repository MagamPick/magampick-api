package com.magampick.favorite.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.magampick.TestcontainersConfiguration;
import com.magampick.customer.domain.Customer;
import com.magampick.customer.repository.CustomerRepository;
import com.magampick.favorite.domain.Favorite;
import com.magampick.global.common.GeometryUtil;
import com.magampick.global.config.JpaAuditingConfig;
import com.magampick.seller.domain.Seller;
import com.magampick.seller.repository.SellerRepository;
import com.magampick.store.domain.OperationStatus;
import com.magampick.store.domain.Store;
import com.magampick.store.repository.StoreRepository;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

/** findFavoriteStoresWithDistance 커스텀 네이티브 쿼리 검증. PostGIS 거리 계산 / 소프트삭제 제외 / 거리무관 전체 반환. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
class FavoriteRepositoryTest {

  // origin: 서울시청
  private static final double ORIGIN_LAT = 37.5665;
  private static final double ORIGIN_LNG = 126.9780;
  // ~280m 거리 매장
  private static final double NEAR_LAT = 37.5685;
  private static final double NEAR_LNG = 126.9800;
  // ~2km 거리 매장
  private static final double FAR_LAT = 37.5490;
  private static final double FAR_LNG = 126.9740;

  @Autowired FavoriteRepository favoriteRepository;
  @Autowired StoreRepository storeRepository;
  @Autowired SellerRepository sellerRepository;
  @Autowired CustomerRepository customerRepository;

  private Customer customer;
  private Seller seller;

  @BeforeEach
  void setUp() {
    customer =
        customerRepository.save(
            Customer.builder()
                .email("fav_" + System.nanoTime() + "@test.com")
                .passwordHash("x")
                .nickname("테스트고객")
                .build());
    seller =
        sellerRepository.save(
            Seller.builder()
                .email("seller_" + System.nanoTime() + "@test.com")
                .passwordHash("x")
                .ownerName("테스트사장")
                .build());
  }

  @Test
  void 거리_계산_정확_미터_단위_반환() {
    Store store = storeRepository.save(newStore("근처매장", NEAR_LAT, NEAR_LNG));
    favoriteRepository.save(Favorite.builder().customer(customer).store(store).build());
    favoriteRepository.flush();

    List<FavoriteStoreCandidate> result =
        favoriteRepository.findFavoriteStoresWithDistance(customer.getId(), ORIGIN_LAT, ORIGIN_LNG);

    assertThat(result).hasSize(1);
    FavoriteStoreCandidate c = result.get(0);
    assertThat(c.getStoreId()).isEqualTo(store.getId());
    // ST_Distance GEOGRAPHY 기준 ~280m — 100~500m 범위로 확인
    assertThat(c.getDistanceMeters()).isBetween(100.0, 500.0);
  }

  @Test
  void 거리_무관_전체_단골_반환() {
    Store nearStore = storeRepository.save(newStore("근처매장", NEAR_LAT, NEAR_LNG));
    Store farStore = storeRepository.save(newStore("먼매장", FAR_LAT, FAR_LNG));
    favoriteRepository.save(Favorite.builder().customer(customer).store(nearStore).build());
    favoriteRepository.save(Favorite.builder().customer(customer).store(farStore).build());
    favoriteRepository.flush();

    List<FavoriteStoreCandidate> result =
        favoriteRepository.findFavoriteStoresWithDistance(customer.getId(), ORIGIN_LAT, ORIGIN_LNG);

    assertThat(result).hasSize(2);
  }

  @Test
  void 소프트삭제_매장은_제외됨() {
    Store store = storeRepository.save(newStore("삭제매장", NEAR_LAT, NEAR_LNG));
    favoriteRepository.save(Favorite.builder().customer(customer).store(store).build());
    // 소프트 삭제 (deletedAt 세팅)
    ReflectionTestUtils.setField(store, "deletedAt", LocalDateTime.now());
    storeRepository.save(store);
    favoriteRepository.flush();

    List<FavoriteStoreCandidate> result =
        favoriteRepository.findFavoriteStoresWithDistance(customer.getId(), ORIGIN_LAT, ORIGIN_LNG);

    assertThat(result).isEmpty();
  }

  @Test
  void createdAt_반환_정상() {
    Store store = storeRepository.save(newStore("매장", NEAR_LAT, NEAR_LNG));
    favoriteRepository.save(Favorite.builder().customer(customer).store(store).build());
    favoriteRepository.flush();

    List<FavoriteStoreCandidate> result =
        favoriteRepository.findFavoriteStoresWithDistance(customer.getId(), ORIGIN_LAT, ORIGIN_LNG);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getCreatedAt())
        .isCloseTo(LocalDateTime.now(), within(10, ChronoUnit.SECONDS));
  }

  @Test
  void 다른_소비자_단골은_포함되지_않음() {
    Customer other =
        customerRepository.save(
            Customer.builder()
                .email("other_" + System.nanoTime() + "@test.com")
                .passwordHash("x")
                .nickname("다른고객")
                .build());
    Store store = storeRepository.save(newStore("매장", NEAR_LAT, NEAR_LNG));
    favoriteRepository.save(Favorite.builder().customer(other).store(store).build());
    favoriteRepository.flush();

    List<FavoriteStoreCandidate> result =
        favoriteRepository.findFavoriteStoresWithDistance(customer.getId(), ORIGIN_LAT, ORIGIN_LNG);

    assertThat(result).isEmpty();
  }

  // ── helper ───────────────────────────────────────────────────────────────────────────────────

  private Store newStore(String name, double lat, double lng) {
    return Store.builder()
        .seller(seller)
        .businessNumber("1234567890")
        .name(name)
        .roadAddress("서울시 중구 테스트로 1")
        .zonecode("04524")
        .location(GeometryUtil.toPoint(lat, lng))
        .phone("02-1234-5678")
        .operationStatus(OperationStatus.OPEN)
        .build();
  }
}
