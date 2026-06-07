package com.magampick.product;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.magampick.TestcontainersConfiguration;
import com.magampick.address.domain.Address;
import com.magampick.address.repository.AddressRepository;
import com.magampick.clearance.domain.ClearanceItem;
import com.magampick.clearance.repository.ClearanceItemRepository;
import com.magampick.customer.domain.Customer;
import com.magampick.customer.repository.CustomerRepository;
import com.magampick.global.common.GeometryUtil;
import com.magampick.global.security.JwtProvider;
import com.magampick.global.security.Role;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 소비자 deal/menu 상세 end-to-end 통합 검증. 실제 DB + JWT 인증 + PostGIS 거리 계산. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ProductDetailIntegrationTest {

  @Autowired MockMvc mockMvc;
  @Autowired CustomerRepository customerRepository;
  @Autowired SellerRepository sellerRepository;
  @Autowired StoreRepository storeRepository;
  @Autowired ProductRepository productRepository;
  @Autowired ClearanceItemRepository clearanceItemRepository;
  @Autowired AddressRepository addressRepository;
  @Autowired JwtProvider jwtProvider;

  // ── 고객 deal 상세 end-to-end ─────────────────────────────────────────────────────────────────

  @Test
  void 고객_토큰으로_deal_상세_200_kind_deal() throws Exception {
    long ts = System.nanoTime();

    // 고객 + 기본 주소지
    Customer customer = customerRepository.save(newCustomer(ts));
    addressRepository.save(defaultAddress(customer));

    // 매장 + 상품 + 떨이
    Seller seller = sellerRepository.save(newSeller(ts));
    Store store = storeRepository.save(newStore(seller, ts));
    Product product = productRepository.save(newProduct(store));
    ClearanceItem item = clearanceItemRepository.save(newClearanceItem(store, product));

    String token = jwtProvider.issueAccessToken(customer.getId(), Role.CUSTOMER);

    mockMvc
        .perform(
            get("/api/v1/clearance-items/{id}", item.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.kind").value("deal"))
        .andExpect(jsonPath("$.data.id").value(item.getId()))
        .andExpect(jsonPath("$.data.storeId").value(store.getId()))
        .andExpect(jsonPath("$.data.storeName").value(store.getName()))
        .andExpect(jsonPath("$.data.distanceKm").isNumber())
        .andExpect(jsonPath("$.data.originalPrice").isNumber())
        .andExpect(jsonPath("$.data.salePrice").isNumber())
        .andExpect(jsonPath("$.data.discountRate").isNumber())
        .andExpect(jsonPath("$.data.dealStatus").isString());
  }

  // ── 고객 menu 상세 end-to-end ─────────────────────────────────────────────────────────────────

  @Test
  void 고객_토큰으로_menu_상세_200_kind_menu() throws Exception {
    long ts = System.nanoTime();

    // 고객 + 기본 주소지
    Customer customer = customerRepository.save(newCustomer(ts));
    addressRepository.save(defaultAddress(customer));

    // 매장 + 상품
    Seller seller = sellerRepository.save(newSeller(ts));
    Store store = storeRepository.save(newStore(seller, ts));
    Product product = productRepository.save(newProduct(store));

    String token = jwtProvider.issueAccessToken(customer.getId(), Role.CUSTOMER);

    mockMvc
        .perform(
            get("/api/v1/products/{id}", product.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.kind").value("menu"))
        .andExpect(jsonPath("$.data.id").value(product.getId()))
        .andExpect(jsonPath("$.data.storeId").value(store.getId()))
        .andExpect(jsonPath("$.data.storeName").value(store.getName()))
        .andExpect(jsonPath("$.data.distanceKm").isNumber())
        .andExpect(jsonPath("$.data.price").isNumber())
        .andExpect(jsonPath("$.data.isOnSale").value(true))
        .andExpect(jsonPath("$.data.rating").value(0.0))
        .andExpect(jsonPath("$.data.reviewCount").value(0));
  }

  // ── helpers ───────────────────────────────────────────────────────────────────────────────────

  private Customer newCustomer(long ts) {
    return Customer.builder()
        .email("pd_int_" + ts + "@test.com")
        .passwordHash("x")
        .nickname("통합테스터")
        .build();
  }

  private Address defaultAddress(Customer customer) {
    return Address.builder()
        .customer(customer)
        .label("집")
        .roadAddress("서울시 강남구 테헤란로 427")
        .zonecode("06158")
        .location(GeometryUtil.toPoint(37.4979, 127.0276))
        .isDefault(true)
        .build();
  }

  private Seller newSeller(long ts) {
    return Seller.builder()
        .email("pd_seller_" + ts + "@test.com")
        .passwordHash("x")
        .ownerName("사장님")
        .build();
  }

  private Store newStore(Seller seller, long ts) {
    return Store.builder()
        .seller(seller)
        .businessNumber(String.valueOf(ts).substring(0, 10))
        .representativeName("홍길동")
        .openDate(LocalDate.of(2024, 3, 15))
        .name("통합테스트매장")
        .roadAddress("서울시 강남구 테헤란로 100")
        .zonecode("06158")
        .location(GeometryUtil.toPoint(37.4981, 127.0279))
        .phone("02-1234-5678")
        .operationStatus(OperationStatus.OPEN)
        .build();
  }

  private Product newProduct(Store store) {
    return Product.builder()
        .store(store)
        .name("크로아상")
        .regularPrice(new BigDecimal("4500"))
        .status(ProductStatus.ON_SALE)
        .category(ProductCategory.BAKERY)
        .build();
  }

  private ClearanceItem newClearanceItem(Store store, Product product) {
    return ClearanceItem.builder()
        .store(store)
        .product(product)
        .name("크로아상")
        .regularPrice(new BigDecimal("4500"))
        .salePrice(new BigDecimal("3000"))
        .totalQuantity(5)
        .pickupStartAt(LocalDate.now().atTime(17, 0))
        .pickupEndAt(LocalDate.now().atTime(21, 0))
        .build();
  }
}
