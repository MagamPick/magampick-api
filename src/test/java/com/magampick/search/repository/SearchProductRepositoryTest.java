package com.magampick.search.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.magampick.TestcontainersConfiguration;
import com.magampick.global.common.GeometryUtil;
import com.magampick.global.config.JpaAuditingConfig;
import com.magampick.product.domain.Product;
import com.magampick.product.domain.ProductCategory;
import com.magampick.product.domain.ProductStatus;
import com.magampick.product.repository.ProductNameSuggestion;
import com.magampick.product.repository.ProductRepository;
import com.magampick.product.repository.ProductSearchCandidate;
import com.magampick.seller.domain.Seller;
import com.magampick.seller.repository.SellerRepository;
import com.magampick.store.domain.OperationStatus;
import com.magampick.store.domain.Store;
import com.magampick.store.repository.StoreRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Phase 9 검색: ProductRepository 신규 쿼리 테스트. searchOnSaleProductsByStoreIds /
 * suggestProductNamesByStoreIds
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
class SearchProductRepositoryTest {

  @Autowired ProductRepository productRepository;
  @Autowired StoreRepository storeRepository;
  @Autowired SellerRepository sellerRepository;

  private Seller seller;

  @BeforeEach
  void setUp() {
    seller =
        sellerRepository.save(
            Seller.builder()
                .email("seller_product_" + System.nanoTime() + "@test.com")
                .passwordHash("x")
                .ownerName("테스트사장")
                .build());
  }

  // ── searchOnSaleProductsByStoreIds ─────────────────────────────────────────────────────────

  @Test
  void 상품명_부분일치_ON_SALE_반환() {
    Store store = saveStore("매장");
    Product product = saveProduct(store, "아메리카노", "3000", ProductStatus.ON_SALE);
    productRepository.flush();

    List<ProductSearchCandidate> result =
        productRepository.searchOnSaleProductsByStoreIds(List.of(store.getId()), "아메리");

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getId()).isEqualTo(product.getId());
    assertThat(result.get(0).getName()).isEqualTo("아메리카노");
    assertThat(result.get(0).getStoreId()).isEqualTo(store.getId());
  }

  @Test
  void 상품명_불일치_제외() {
    Store store = saveStore("매장");
    saveProduct(store, "완전다른이름", "3000", ProductStatus.ON_SALE);
    productRepository.flush();

    List<ProductSearchCandidate> result =
        productRepository.searchOnSaleProductsByStoreIds(List.of(store.getId()), "아메리카노");

    assertThat(result).isEmpty();
  }

  @Test
  void storeIds_외부_매장_제외() {
    Store store1 = saveStore("매장1");
    Store store2 = saveStore("매장2");
    saveProduct(store2, "아메리카노", "3000", ProductStatus.ON_SALE);
    productRepository.flush();

    List<ProductSearchCandidate> result =
        productRepository.searchOnSaleProductsByStoreIds(List.of(store1.getId()), "아메리카노");

    assertThat(result).isEmpty();
  }

  @Test
  void ON_SALE이_아닌_상품_제외() {
    Store store = saveStore("매장");
    saveProduct(store, "아메리카노", "3000", ProductStatus.SOLD_OUT);
    productRepository.flush();

    List<ProductSearchCandidate> result =
        productRepository.searchOnSaleProductsByStoreIds(List.of(store.getId()), "아메리카노");

    assertThat(result).isEmpty();
  }

  @Test
  void 소프트삭제된_상품_제외() {
    Store store = saveStore("매장");
    Product product = saveProduct(store, "아메리카노", "3000", ProductStatus.ON_SALE);
    product.softDelete();
    productRepository.save(product);
    productRepository.flush();

    List<ProductSearchCandidate> result =
        productRepository.searchOnSaleProductsByStoreIds(List.of(store.getId()), "아메리카노");

    assertThat(result).isEmpty();
  }

  @Test
  void 응답_필드_정확히_반환() {
    Store store = saveStore("매장");
    Product product = saveProduct(store, "아메리카노", "4500", ProductStatus.ON_SALE);
    productRepository.flush();

    List<ProductSearchCandidate> result =
        productRepository.searchOnSaleProductsByStoreIds(List.of(store.getId()), "아메리카노");

    assertThat(result).hasSize(1);
    ProductSearchCandidate c = result.get(0);
    assertThat(c.getStoreId()).isEqualTo(store.getId());
    assertThat(c.getName()).isEqualTo("아메리카노");
    assertThat(c.getRegularPrice()).isEqualByComparingTo(new BigDecimal("4500"));
    assertThat(c.getImageUrl()).isNull();
  }

  // ── suggestProductNamesByStoreIds ──────────────────────────────────────────────────────────

  @Test
  void 상품_자동완성_word_similarity_threshold_이상_반환() {
    Store store = saveStore("매장");
    saveProduct(store, "아메리카노", "3000", ProductStatus.ON_SALE);
    productRepository.flush();

    List<ProductNameSuggestion> result =
        productRepository.suggestProductNamesByStoreIds(List.of(store.getId()), "아메", 0.3);

    assertThat(result).isNotEmpty();
    assertThat(result.get(0).getName()).isEqualTo("아메리카노");
    assertThat(result.get(0).getSimilarity()).isGreaterThanOrEqualTo(0.3);
  }

  @Test
  void 상품_자동완성_유사도_낮은_경우_제외() {
    Store store = saveStore("매장");
    saveProduct(store, "완전다른이름", "3000", ProductStatus.ON_SALE);
    productRepository.flush();

    List<ProductNameSuggestion> result =
        productRepository.suggestProductNamesByStoreIds(List.of(store.getId()), "아메리카노", 0.3);

    assertThat(result).isEmpty();
  }

  @Test
  void 상품_자동완성_storeIds_외부_매장_제외() {
    Store store1 = saveStore("매장1");
    Store store2 = saveStore("매장2");
    saveProduct(store2, "아메리카노", "3000", ProductStatus.ON_SALE);
    productRepository.flush();

    List<ProductNameSuggestion> result =
        productRepository.suggestProductNamesByStoreIds(List.of(store1.getId()), "아메리카노", 0.3);

    assertThat(result).isEmpty();
  }

  // ── helper ───────────────────────────────────────────────────────────────────────────────────

  private Store saveStore(String name) {
    return storeRepository.save(
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
  }

  private Product saveProduct(Store store, String name, String price, ProductStatus status) {
    Product product =
        Product.builder()
            .store(store)
            .name(name)
            .regularPrice(new BigDecimal(price))
            .status(status)
            .category(ProductCategory.ETC)
            .build();
    return productRepository.save(product);
  }
}
