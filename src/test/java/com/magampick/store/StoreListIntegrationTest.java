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

/** 전체 매장 조회 (소비자) end-to-end 통합 테스트. 고객 → 기본 주소지 → 5km 이내 매장 → 정렬 결과 확인. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class StoreListIntegrationTest {

  // 서울시청 인근 origin
  private static final double ORIGIN_LAT = 37.5665;
  private static final double ORIGIN_LNG = 126.9780;
  // 약 280m — 5km 이내
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
  @Autowired JwtProvider jwtProvider;

  @Test
  void 고객_기본주소지_5km이내_매장_조회_성공() throws Exception {
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

    // given — 5km 이내 OPEN 매장 (오늘 영업)
    Seller seller = sellerRepository.save(newSeller());
    Store store =
        storeRepository.save(newStore(seller, "동네빵집", NEAR_LAT, NEAR_LNG, OperationStatus.OPEN));
    storeBusinessHourRepository.save(
        StoreBusinessHour.builder()
            .store(store)
            .dayOfWeek(LocalDate.now().getDayOfWeek())
            .openTime(LocalTime.of(9, 0))
            .closeTime(LocalTime.of(21, 0))
            .build());

    String token = jwtProvider.issueAccessToken(customer.getId(), Role.CUSTOMER);

    // when
    MvcResult result =
        mockMvc
            .perform(
                get("/api/v1/stores?sort=recommended")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();

    // then
    JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    assertThat(data.path("total").asLong()).isGreaterThanOrEqualTo(1L);
    assertThat(data.path("items").isArray()).isTrue();

    boolean found = false;
    for (JsonNode item : data.path("items")) {
      if (item.path("id").asLong() == store.getId()) {
        found = true;
        assertThat(item.path("name").asText()).isEqualTo("동네빵집");
        assertThat(item.path("distanceKm").asDouble()).isLessThan(1.0); // 약 0.28km
        break;
      }
    }
    assertThat(found).isTrue();
  }

  @Test
  void 미인증_요청_401() throws Exception {
    mockMvc.perform(get("/api/v1/stores")).andExpect(status().isUnauthorized());
  }

  @Test
  void 기본주소지_없는_고객_400() throws Exception {
    // 주소지 없는 고객
    Customer customer = customerRepository.save(newCustomer());
    String token = jwtProvider.issueAccessToken(customer.getId(), Role.CUSTOMER);

    mockMvc
        .perform(get("/api/v1/stores").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("DEFAULT_ADDRESS_REQUIRED"));
  }

  @Test
  void 정렬_파라미터_5종_모두_200_반환() throws Exception {
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

    for (String sort : new String[] {"recommended", "distance", "discount", "closing", "rating"}) {
      mockMvc
          .perform(
              get("/api/v1/stores?sort=" + sort)
                  .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
          .andExpect(status().isOk());
    }
  }

  @Test
  void 활성_떨이_있는_매장_dealStoreCount_집계() throws Exception {
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

    Seller seller = sellerRepository.save(newSeller());
    Store store =
        storeRepository.save(newStore(seller, "떨이매장", NEAR_LAT, NEAR_LNG, OperationStatus.OPEN));
    storeBusinessHourRepository.save(
        StoreBusinessHour.builder()
            .store(store)
            .dayOfWeek(LocalDate.now().getDayOfWeek())
            .openTime(LocalTime.of(9, 0))
            .closeTime(LocalTime.of(21, 0))
            .build());
    clearanceItemRepository.save(
        ClearanceItem.builder()
            .store(store)
            .name("크로아상 떨이")
            .regularPrice(new BigDecimal("4500"))
            .salePrice(new BigDecimal("3000"))
            .totalQuantity(5)
            .pickupStartAt(LocalDateTime.now().minusHours(1))
            .pickupEndAt(LocalDateTime.now().plusHours(3))
            .build());

    String token = jwtProvider.issueAccessToken(customer.getId(), Role.CUSTOMER);

    MvcResult result =
        mockMvc
            .perform(get("/api/v1/stores").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();

    JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    // dealStoreCount >= 1 (이 매장 포함)
    assertThat(data.path("dealStoreCount").asLong()).isGreaterThanOrEqualTo(1L);
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

  private Store newStore(
      Seller seller, String name, double lat, double lng, OperationStatus status) {
    return Store.builder()
        .seller(seller)
        .businessNumber("1234567890")
        .representativeName("홍길동")
        .openDate(LocalDate.of(2024, 3, 15))
        .name(name)
        .roadAddress("서울시 중구 테스트로 1")
        .zonecode("04524")
        .location(GeometryUtil.toPoint(lat, lng))
        .phone("02-1234-5678")
        .operationStatus(status)
        .build();
  }
}
