package com.magampick.product.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.magampick.TestcontainersConfiguration;
import com.magampick.global.common.GeometryUtil;
import com.magampick.global.config.JpaAuditingConfig;
import com.magampick.product.domain.Product;
import com.magampick.product.domain.ProductCategory;
import com.magampick.product.domain.ProductStatus;
import com.magampick.seller.domain.Seller;
import com.magampick.seller.repository.SellerRepository;
import com.magampick.store.domain.OperationStatus;
import com.magampick.store.domain.Store;
import com.magampick.store.repository.StoreRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
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
class ProductDetailRepositoryTest {

  @Autowired ProductRepository productRepository;
  @Autowired StoreRepository storeRepository;
  @Autowired SellerRepository sellerRepository;

  private Store store;

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

    store =
        storeRepository.save(
            Store.builder()
                .seller(seller)
                .businessNumber("1234567890")
                .representativeName("홍길동")
                .openDate(LocalDate.of(2024, 3, 15))
                .name("테스트매장")
                .roadAddress("서울시 중구 테스트로 1")
                .zonecode("04524")
                .location(GeometryUtil.toPoint(37.5665, 126.9780))
                .phone("02-1234-5678")
                .operationStatus(OperationStatus.OPEN)
                .build());
  }

  // ── findByIdAndDeletedAtIsNull: store 함께 초기화 ────────────────────────────────────────────────

  @Test
  void findByIdAndDeletedAtIsNull_store_초기화() {
    Product saved =
        productRepository.save(
            Product.builder()
                .store(store)
                .name("크로아상")
                .regularPrice(new BigDecimal("4500"))
                .status(ProductStatus.ON_SALE)
                .category(ProductCategory.BAKERY)
                .build());
    productRepository.flush();

    Optional<Product> result = productRepository.findByIdAndDeletedAtIsNull(saved.getId());

    assertThat(result).isPresent();
    // EntityGraph 로 store fetch → LazyInitializationException 없이 접근 가능
    assertThat(Hibernate.isInitialized(result.get().getStore())).isTrue();
    assertThat(result.get().getStore().getName()).isEqualTo("테스트매장");
  }

  @Test
  void findByIdAndDeletedAtIsNull_없는_id_빈_Optional() {
    Optional<Product> result = productRepository.findByIdAndDeletedAtIsNull(99999L);

    assertThat(result).isEmpty();
  }

  @Test
  void findByIdAndDeletedAtIsNull_삭제된_상품_빈_Optional() {
    Product saved =
        productRepository.save(
            Product.builder()
                .store(store)
                .name("크로아상")
                .regularPrice(new BigDecimal("4500"))
                .status(ProductStatus.ON_SALE)
                .category(ProductCategory.BAKERY)
                .build());
    saved.softDelete();
    productRepository.flush();

    Optional<Product> result = productRepository.findByIdAndDeletedAtIsNull(saved.getId());

    assertThat(result).isEmpty();
  }
}
