package com.magampick.store.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.magampick.TestcontainersConfiguration;
import com.magampick.clearance.domain.ClearanceItem;
import com.magampick.clearance.domain.ClearanceItemStatus;
import com.magampick.clearance.repository.ClearanceItemRepository;
import com.magampick.global.common.GeometryUtil;
import com.magampick.global.config.JpaAuditingConfig;
import com.magampick.product.domain.Product;
import com.magampick.product.domain.ProductCategory;
import com.magampick.product.domain.ProductStatus;
import com.magampick.product.repository.ProductRepository;
import com.magampick.seller.domain.Seller;
import com.magampick.seller.repository.SellerRepository;
import com.magampick.store.domain.OperationStatus;
import com.magampick.store.domain.Store;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** StoreRepository(단건 조회·거리), ProductRepository(ON_SALE), ClearanceItemRepository(활성 떨이) 쿼리 검증. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
class StoreDetailRepositoryTest {

  // origin: 서울시청 인근
  private static final double ORIGIN_LAT = 37.5665;
  private static final double ORIGIN_LNG = 126.9780;

  // 약 280m — 5km 이내
  private static final double STORE_LAT = 37.5685;
  private static final double STORE_LNG = 126.9800;

  @Autowired StoreRepository storeRepository;
  @Autowired ProductRepository productRepository;
  @Autowired ClearanceItemRepository clearanceItemRepository;
  @Autowired SellerRepository sellerRepository;

  private Seller seller;
  private Store store;

  @BeforeEach
  void setUp() {
    seller =
        sellerRepository.save(
            Seller.builder()
                .email("s_" + System.nanoTime() + "@test.com")
                .passwordHash("x")
                .ownerName("사장")
                .build());
    store =
        storeRepository.save(
            Store.builder()
                .seller(seller)
                .businessNumber("1234567890")
                .representativeName("홍길동")
                .openDate(LocalDate.of(2024, 3, 15))
                .name("테스트매장")
                .roadAddress("서울시 중구 1")
                .zonecode("04524")
                .location(GeometryUtil.toPoint(STORE_LAT, STORE_LNG))
                .phone("02-0000-0000")
                .operationStatus(OperationStatus.OPEN)
                .build());
  }

  // ── StoreRepository.findByIdAndDeletedAtIsNull ─────────────────────────────────────────────

  @Test
  void 매장_단건_조회_성공() {
    Optional<Store> found = storeRepository.findByIdAndDeletedAtIsNull(store.getId());
    assertThat(found).isPresent();
    assertThat(found.get().getName()).isEqualTo("테스트매장");
  }

  @Test
  void 없는_매장_빈_Optional() {
    Optional<Store> found = storeRepository.findByIdAndDeletedAtIsNull(9999L);
    assertThat(found).isEmpty();
  }

  // ── StoreRepository.findDistanceMeters ─────────────────────────────────────────────────────

  @Test
  void 단건_거리_약_280m() {
    Double meters = storeRepository.findDistanceMeters(store.getId(), ORIGIN_LAT, ORIGIN_LNG);
    assertThat(meters).isNotNull().isCloseTo(280.0, within(50.0)); // ±50m 허용
  }

  // ── ProductRepository.findByStoreIdAndStatusAndDeletedAtIsNull ────────────────────────────

  @Test
  void ON_SALE_상품만_조회() {
    productRepository.save(
        Product.builder()
            .store(store)
            .name("A")
            .regularPrice(new BigDecimal("3000"))
            .status(ProductStatus.ON_SALE)
            .category(ProductCategory.BAKERY)
            .build());
    productRepository.save(
        Product.builder()
            .store(store)
            .name("B")
            .regularPrice(new BigDecimal("2000"))
            .status(ProductStatus.SOLD_OUT)
            .category(ProductCategory.ETC)
            .build());

    List<Product> result =
        productRepository.findByStoreIdAndStatusAndDeletedAtIsNull(
            store.getId(), ProductStatus.ON_SALE);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getName()).isEqualTo("A");
  }

  // ── ClearanceItemRepository.findByStoreIdAndStatus ─────────────────────────────────────────

  @Test
  void 활성_OPEN_떨이만_조회() {
    clearanceItemRepository.save(
        ClearanceItem.builder()
            .store(store)
            .name("크로아상")
            .regularPrice(new BigDecimal("5000"))
            .salePrice(new BigDecimal("3000"))
            .totalQuantity(5)
            .pickupStartAt(LocalDateTime.now().minusHours(1))
            .pickupEndAt(LocalDateTime.now().plusHours(3))
            .build()); // status = OPEN (기본값)

    ClearanceItem closed =
        clearanceItemRepository.save(
            ClearanceItem.builder()
                .store(store)
                .name("마감")
                .regularPrice(new BigDecimal("3000"))
                .salePrice(new BigDecimal("1500"))
                .totalQuantity(3)
                .pickupStartAt(LocalDateTime.now().minusHours(5))
                .pickupEndAt(LocalDateTime.now().minusHours(1))
                .build());
    closed.close(); // CLOSED 상태로 변경
    clearanceItemRepository.save(closed);

    List<ClearanceItem> result =
        clearanceItemRepository.findByStoreIdAndStatus(store.getId(), ClearanceItemStatus.OPEN);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getName()).isEqualTo("크로아상");
  }
}
