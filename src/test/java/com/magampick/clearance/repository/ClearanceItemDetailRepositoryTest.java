package com.magampick.clearance.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.magampick.TestcontainersConfiguration;
import com.magampick.clearance.domain.ClearanceItem;
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
import com.magampick.store.repository.StoreRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.hibernate.Hibernate;
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
class ClearanceItemDetailRepositoryTest {

  @Autowired ClearanceItemRepository clearanceItemRepository;
  @Autowired StoreRepository storeRepository;
  @Autowired SellerRepository sellerRepository;
  @Autowired ProductRepository productRepository;

  private Store store;
  private Product product;

  @BeforeEach
  void setUp() {
    long ts = System.nanoTime();
    Seller seller =
        sellerRepository.save(
            Seller.builder()
                .email("seller_" + ts + "@test.com")
                .passwordHash("x")
                .ownerName("테스트사장")
                .build());

    store = saveStore(seller);
    product = saveProduct(store);
  }

  // ── findByIdWithStoreAndProduct: store + product 함께 초기화 ──────────────────────────────────────

  @Test
  void findByIdWithStoreAndProduct_store_product_초기화() {
    ClearanceItem saved = saveClearanceItem(store, product);
    clearanceItemRepository.flush();

    Optional<ClearanceItem> result =
        clearanceItemRepository.findByIdWithStoreAndProduct(saved.getId());

    assertThat(result).isPresent();
    ClearanceItem item = result.get();
    // EntityGraph 로 fetch → LazyInitializationException 없이 접근 가능
    assertThat(Hibernate.isInitialized(item.getStore())).isTrue();
    assertThat(Hibernate.isInitialized(item.getProduct())).isTrue();
    assertThat(item.getStore().getName()).isEqualTo("테스트매장");
    assertThat(item.getProduct().getName()).isEqualTo("크로아상");
  }

  @Test
  void findByIdWithStoreAndProduct_없는_id_빈_Optional() {
    Optional<ClearanceItem> result = clearanceItemRepository.findByIdWithStoreAndProduct(99999L);

    assertThat(result).isEmpty();
  }

  // ── helpers ───────────────────────────────────────────────────────────────────────────────────

  private Store saveStore(Seller seller) {
    return storeRepository.save(
        Store.builder()
            .seller(seller)
            .businessNumber("1234567890")
            .name("테스트매장")
            .roadAddress("서울시 중구 테스트로 1")
            .zonecode("04524")
            .location(GeometryUtil.toPoint(37.5665, 126.9780))
            .phone("02-1234-5678")
            .operationStatus(OperationStatus.OPEN)
            .build());
  }

  private Product saveProduct(Store s) {
    return productRepository.save(
        Product.builder()
            .store(s)
            .name("크로아상")
            .regularPrice(new BigDecimal("4500"))
            .status(ProductStatus.ON_SALE)
            .category(ProductCategory.BAKERY)
            .build());
  }

  private ClearanceItem saveClearanceItem(Store s, Product p) {
    return clearanceItemRepository.save(
        ClearanceItem.builder()
            .store(s)
            .product(p)
            .name("크로아상 떨이")
            .regularPrice(new BigDecimal("4500"))
            .salePrice(new BigDecimal("3000"))
            .totalQuantity(5)
            .pickupStartAt(LocalDate.now().atTime(17, 0))
            .pickupEndAt(LocalDate.now().atTime(21, 0))
            .build());
  }

  private static LocalDateTime tomorrowAt(int hour, int minute) {
    return LocalDate.now().plusDays(1).atTime(hour, minute);
  }
}
