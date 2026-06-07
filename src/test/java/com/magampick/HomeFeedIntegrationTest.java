package com.magampick;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.magampick.address.domain.Address;
import com.magampick.address.repository.AddressRepository;
import com.magampick.clearance.domain.ClearanceItem;
import com.magampick.clearance.repository.ClearanceItemRepository;
import com.magampick.customer.domain.Customer;
import com.magampick.customer.repository.CustomerRepository;
import com.magampick.favorite.domain.Favorite;
import com.magampick.favorite.repository.FavoriteRepository;
import com.magampick.global.common.GeometryUtil;
import com.magampick.global.security.JwtProvider;
import com.magampick.global.security.Role;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 홈 피드 end-to-end 통합 테스트. closing-soon(60분 윈도우) + neighborhood(단골 제외) 두 섹션을 한 컨텍스트에서 검증. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class HomeFeedIntegrationTest {

  // origin: 서울시청
  private static final double ORIGIN_LAT = 37.5665;
  private static final double ORIGIN_LNG = 126.9780;
  // 5km 이내 (~280m)
  private static final double NEAR_LAT = 37.5685;
  private static final double NEAR_LNG = 126.9800;

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @Autowired CustomerRepository customerRepository;
  @Autowired AddressRepository addressRepository;
  @Autowired SellerRepository sellerRepository;
  @Autowired StoreRepository storeRepository;
  @Autowired StoreBusinessHourRepository storeBusinessHourRepository;
  @Autowired ClearanceItemRepository clearanceItemRepository;
  @Autowired FavoriteRepository favoriteRepository;
  @Autowired JwtProvider jwtProvider;

  // ── closing-soon ──────────────────────────────────────────────────────────────────────────────

  @Test
  void closing_soon_60분_이내_떨이_반환() throws Exception {
    Customer customer = customerRepository.save(newCustomer());
    addressRepository.save(defaultAddress(customer));

    Seller seller = sellerRepository.save(newSeller());
    Store store = storeRepository.save(newStore(seller, "빵집"));
    storeBusinessHourRepository.save(todayBusinessHour(store));

    // 30분 후 마감 → 포함
    LocalDateTime deadline = LocalDateTime.now().plusMinutes(30);
    clearanceItemRepository.save(
        ClearanceItem.builder()
            .store(store)
            .name("크로아상 특가")
            .regularPrice(new BigDecimal("4500"))
            .salePrice(new BigDecimal("3000"))
            .totalQuantity(5)
            .pickupStartAt(LocalDateTime.now().minusHours(1))
            .pickupEndAt(deadline)
            .build());

    String token = jwtProvider.issueAccessToken(customer.getId(), Role.CUSTOMER);

    MvcResult result =
        mockMvc
            .perform(
                get("/api/v1/clearance-items/closing-soon")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();

    JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    assertThat(data.isArray()).isTrue();

    boolean found = false;
    for (JsonNode item : data) {
      if ("크로아상 특가".equals(item.path("productName").asText())
          && "빵집".equals(item.path("storeName").asText())) {
        found = true;
        assertThat(item.path("discountRate").asInt()).isEqualTo(33); // (4500-3000)/4500*100 ≈ 33
        break;
      }
    }
    assertThat(found).isTrue();
  }

  @Test
  void closing_soon_60분_초과_떨이는_제외() throws Exception {
    Customer customer = customerRepository.save(newCustomer());
    addressRepository.save(defaultAddress(customer));

    Seller seller = sellerRepository.save(newSeller());
    Store store = storeRepository.save(newStore(seller, "제외매장"));
    storeBusinessHourRepository.save(todayBusinessHour(store));

    // 90분 후 마감 → 제외
    clearanceItemRepository.save(
        ClearanceItem.builder()
            .store(store)
            .name("먼미래떨이")
            .regularPrice(new BigDecimal("3000"))
            .salePrice(new BigDecimal("2000"))
            .totalQuantity(3)
            .pickupStartAt(LocalDateTime.now().minusHours(1))
            .pickupEndAt(LocalDateTime.now().plusMinutes(90))
            .build());

    String token = jwtProvider.issueAccessToken(customer.getId(), Role.CUSTOMER);

    MvcResult result =
        mockMvc
            .perform(
                get("/api/v1/clearance-items/closing-soon")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();

    JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    for (JsonNode item : data) {
      assertThat(item.path("productName").asText()).isNotEqualTo("먼미래떨이");
    }
  }

  @Test
  void closing_soon_미인증_401() throws Exception {
    mockMvc
        .perform(get("/api/v1/clearance-items/closing-soon"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void closing_soon_기본주소지_없으면_400() throws Exception {
    Customer customer = customerRepository.save(newCustomer());
    // 주소지 등록 안 함
    String token = jwtProvider.issueAccessToken(customer.getId(), Role.CUSTOMER);

    mockMvc
        .perform(
            get("/api/v1/clearance-items/closing-soon")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("DEFAULT_ADDRESS_REQUIRED"));
  }

  // ── neighborhood ─────────────────────────────────────────────────────────────────────────────

  @Test
  void neighborhood_단골_제외_후_반환() throws Exception {
    Customer customer = customerRepository.save(newCustomer());
    addressRepository.save(defaultAddress(customer));

    Seller seller = sellerRepository.save(newSeller());
    Store normalStore = storeRepository.save(newStore(seller, "일반매장"));
    Store favoriteStore = storeRepository.save(newStore(seller, "단골매장"));
    storeBusinessHourRepository.save(todayBusinessHour(normalStore));
    storeBusinessHourRepository.save(todayBusinessHour(favoriteStore));

    // favoriteStore 를 단골 등록
    favoriteRepository.save(Favorite.builder().customer(customer).store(favoriteStore).build());

    String token = jwtProvider.issueAccessToken(customer.getId(), Role.CUSTOMER);

    MvcResult result =
        mockMvc
            .perform(
                get("/api/v1/stores/neighborhood")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();

    JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    assertThat(data.isArray()).isTrue();

    // 단골매장은 결과에 없어야 함
    for (JsonNode item : data) {
      assertThat(item.path("name").asText()).isNotEqualTo("단골매장");
    }

    // 일반매장은 결과에 있어야 함
    boolean foundNormal = false;
    for (JsonNode item : data) {
      if ("일반매장".equals(item.path("name").asText())) {
        foundNormal = true;
        break;
      }
    }
    assertThat(foundNormal).isTrue();
  }

  @Test
  void neighborhood_미인증_401() throws Exception {
    mockMvc.perform(get("/api/v1/stores/neighborhood")).andExpect(status().isUnauthorized());
  }

  @Test
  void neighborhood_기본주소지_없으면_400() throws Exception {
    Customer customer = customerRepository.save(newCustomer());
    String token = jwtProvider.issueAccessToken(customer.getId(), Role.CUSTOMER);

    mockMvc
        .perform(
            get("/api/v1/stores/neighborhood").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("DEFAULT_ADDRESS_REQUIRED"));
  }

  // ── helper ───────────────────────────────────────────────────────────────────────────────────

  private Customer newCustomer() {
    return Customer.builder()
        .email("customer_" + System.nanoTime() + "@test.com")
        .passwordHash("x")
        .nickname("테스트고객")
        .build();
  }

  private Seller newSeller() {
    return Seller.builder()
        .email("seller_" + System.nanoTime() + "@test.com")
        .passwordHash("x")
        .ownerName("테스트사장")
        .build();
  }

  private Address defaultAddress(Customer customer) {
    return Address.builder()
        .customer(customer)
        .label("집")
        .roadAddress("서울시 중구 테스트로 1")
        .zonecode("04524")
        .location(GeometryUtil.toPoint(ORIGIN_LAT, ORIGIN_LNG))
        .isDefault(true)
        .build();
  }

  private Store newStore(Seller seller, String name) {
    return Store.builder()
        .seller(seller)
        .businessNumber("1234567890")
        .representativeName("홍길동")
        .openDate(LocalDate.of(2024, 3, 15))
        .name(name)
        .roadAddress("서울시 중구 테스트로 1")
        .zonecode("04524")
        .location(GeometryUtil.toPoint(NEAR_LAT, NEAR_LNG))
        .phone("02-1234-5678")
        .operationStatus(OperationStatus.OPEN)
        .build();
  }

  private StoreBusinessHour todayBusinessHour(Store store) {
    return StoreBusinessHour.builder()
        .store(store)
        .dayOfWeek(LocalDate.now().getDayOfWeek())
        .openTime(LocalTime.of(9, 0))
        .closeTime(LocalTime.of(21, 0))
        .build();
  }
}
