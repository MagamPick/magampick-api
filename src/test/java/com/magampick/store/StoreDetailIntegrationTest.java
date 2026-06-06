package com.magampick.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.magampick.store.domain.StoreBusinessHour;
import com.magampick.store.repository.StoreBusinessHourRepository;
import com.magampick.store.repository.StoreRepository;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 소비자 매장 상세 조회 end-to-end 통합 테스트. 고객 → 기본 주소지 → store
 * 상세(businessStatus·closingTime·rating·distance) 검증.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class StoreDetailIntegrationTest {

  // origin: 서울시청 인근
  private static final double ORIGIN_LAT = 37.5665;
  private static final double ORIGIN_LNG = 126.9780;

  // 약 280m
  private static final double STORE_LAT = 37.5685;
  private static final double STORE_LNG = 126.9800;

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @Autowired CustomerRepository customerRepository;
  @Autowired AddressRepository addressRepository;
  @Autowired SellerRepository sellerRepository;
  @Autowired StoreRepository storeRepository;
  @Autowired StoreBusinessHourRepository storeBusinessHourRepository;
  @Autowired ProductRepository productRepository;
  @Autowired ClearanceItemRepository clearanceItemRepository;
  @Autowired JwtProvider jwtProvider;

  @Test
  void 고객_매장상세_조회_성공() throws Exception {
    // given — 고객 + 기본 주소지
    Customer customer = customerRepository.save(newCustomer());
    addressRepository.save(
        Address.builder()
            .customer(customer)
            .label("집")
            .roadAddress("서울시 중구 테스트로 1")
            .zonecode("04524")
            .location(GeometryUtil.toPoint(ORIGIN_LAT, ORIGIN_LNG))
            .isDefault(true)
            .build());

    // given — 매장 + 오늘 영업시간
    Seller seller = sellerRepository.save(newSeller());
    Store store =
        storeRepository.save(
            Store.builder()
                .seller(seller)
                .businessNumber("9876543210")
                .name("통합테스트매장")
                .roadAddress("서울시 중구 통합로 1")
                .zonecode("04524")
                .location(GeometryUtil.toPoint(STORE_LAT, STORE_LNG))
                .phone("02-9999-9999")
                .operationStatus(OperationStatus.OPEN)
                .build());

    // 오늘 요일 영업시간 등록
    DayOfWeek today = LocalDate.now().getDayOfWeek();
    storeBusinessHourRepository.save(
        StoreBusinessHour.builder()
            .store(store)
            .dayOfWeek(today)
            .openTime(LocalTime.of(9, 0))
            .closeTime(LocalTime.of(21, 0))
            .build());

    // given — 상품 (메뉴탭용)
    productRepository.save(
        Product.builder()
            .store(store)
            .name("크로아상")
            .regularPrice(new BigDecimal("3500"))
            .status(ProductStatus.ON_SALE)
            .category(ProductCategory.BAKERY)
            .build());

    // given — 활성 떨이 (deals탭용)
    clearanceItemRepository.save(
        ClearanceItem.builder()
            .store(store)
            .name("마감 세트")
            .regularPrice(new BigDecimal("5000"))
            .salePrice(new BigDecimal("3000"))
            .totalQuantity(3)
            .pickupStartAt(LocalDateTime.now().minusHours(1))
            .pickupEndAt(LocalDateTime.now().plusHours(2))
            .build());

    String token = jwtProvider.issueAccessToken(customer.getId(), Role.CUSTOMER);

    // when — 매장 상세
    MvcResult result =
        mockMvc
            .perform(
                get("/api/v1/stores/{id}", store.getId())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();

    // then
    JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    assertThat(data.path("id").asLong()).isEqualTo(store.getId());
    assertThat(data.path("name").asText()).isEqualTo("통합테스트매장");
    assertThat(data.path("businessStatus").asText()).isEqualTo("OPEN");
    assertThat(data.path("distanceKm").asDouble()).isLessThan(1.0); // 약 0.28km
    assertThat(data.path("closingTime").asText()).isEqualTo("21:00"); // 오늘 영업
    assertThat(data.path("operatingHours").isArray()).isTrue();
    assertThat(data.path("operatingHours").size()).isEqualTo(7);
    assertThat(data.path("lat").asDouble()).isGreaterThan(0);
    assertThat(data.path("lng").asDouble()).isGreaterThan(0);
  }

  @Test
  void 미인증_매장상세_401() throws Exception {
    Seller seller = sellerRepository.save(newSeller());
    Store store = storeRepository.save(newStore(seller, OperationStatus.OPEN));

    mockMvc.perform(get("/api/v1/stores/{id}", store.getId())).andExpect(status().isUnauthorized());
  }

  @Test
  void 매장_없음_404() throws Exception {
    Customer customer = customerRepository.save(newCustomer());
    addressRepository.save(
        Address.builder()
            .customer(customer)
            .label("집")
            .roadAddress("서울시 중구 테스트로 1")
            .zonecode("04524")
            .location(GeometryUtil.toPoint(ORIGIN_LAT, ORIGIN_LNG))
            .isDefault(true)
            .build());
    String token = jwtProvider.issueAccessToken(customer.getId(), Role.CUSTOMER);

    mockMvc
        .perform(
            get("/api/v1/stores/{id}", 99999L).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("STORE_NOT_FOUND"));
  }

  @Test
  void 마감할인_탭_공개_조회_성공() throws Exception {
    Seller seller = sellerRepository.save(newSeller());
    Store store = storeRepository.save(newStore(seller, OperationStatus.OPEN));
    clearanceItemRepository.save(
        ClearanceItem.builder()
            .store(store)
            .name("떨이빵")
            .regularPrice(new BigDecimal("4000"))
            .salePrice(new BigDecimal("2000"))
            .totalQuantity(5)
            .pickupStartAt(LocalDateTime.now().minusHours(1))
            .pickupEndAt(LocalDateTime.now().plusHours(2))
            .build());

    MvcResult result =
        mockMvc
            .perform(get("/api/v1/stores/{id}/clearance-items", store.getId()))
            .andExpect(status().isOk())
            .andReturn();

    JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    assertThat(data.isArray()).isTrue();
    assertThat(data.size()).isGreaterThanOrEqualTo(1);
    // discountRate = 50% (4000 → 2000)
    assertThat(data.get(0).path("discountRate").asInt()).isEqualTo(50);
  }

  @Test
  void 메뉴_탭_공개_조회_성공() throws Exception {
    Seller seller = sellerRepository.save(newSeller());
    Store store = storeRepository.save(newStore(seller, OperationStatus.OPEN));
    productRepository.save(
        Product.builder()
            .store(store)
            .name("테스트빵")
            .regularPrice(new BigDecimal("3000"))
            .status(ProductStatus.ON_SALE)
            .category(ProductCategory.BAKERY)
            .build());

    MvcResult result =
        mockMvc
            .perform(get("/api/v1/stores/{id}/menu", store.getId()))
            .andExpect(status().isOk())
            .andReturn();

    JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    assertThat(data.isArray()).isTrue();
    assertThat(data.size()).isGreaterThanOrEqualTo(1);
    assertThat(data.get(0).path("category").asText()).isEqualTo("베이커리");
  }

  // ── helpers ───────────────────────────────────────────────────────────────────────────────────

  private Customer newCustomer() {
    return Customer.builder()
        .email("cust_" + System.nanoTime() + "@test.com")
        .passwordHash("x")
        .nickname("고객")
        .build();
  }

  private Seller newSeller() {
    return Seller.builder()
        .email("sell_" + System.nanoTime() + "@test.com")
        .passwordHash("x")
        .ownerName("사장")
        .build();
  }

  private Store newStore(Seller seller, OperationStatus status) {
    return Store.builder()
        .seller(seller)
        .businessNumber("1234567890")
        .name("매장_" + System.nanoTime())
        .roadAddress("서울시 중구 1")
        .zonecode("04524")
        .location(GeometryUtil.toPoint(STORE_LAT, STORE_LNG))
        .phone("02-0000-0000")
        .operationStatus(status)
        .build();
  }
}
