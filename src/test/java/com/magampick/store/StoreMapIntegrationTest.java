package com.magampick.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.magampick.TestcontainersConfiguration;
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

/** 지도 기반 매장 조회 end-to-end 통합 테스트. GPS 좌표 → 반경 내 매장 마커 목록 확인. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class StoreMapIntegrationTest {

  // 서울시청 인근
  private static final double ORIGIN_LAT = 37.5665;
  private static final double ORIGIN_LNG = 126.9780;
  // 약 280m — 1km 반경 이내
  private static final double NEAR_LAT = 37.5685;
  private static final double NEAR_LNG = 126.9800;

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @Autowired CustomerRepository customerRepository;
  @Autowired SellerRepository sellerRepository;
  @Autowired StoreRepository storeRepository;
  @Autowired StoreBusinessHourRepository storeBusinessHourRepository;
  @Autowired ClearanceItemRepository clearanceItemRepository;
  @Autowired JwtProvider jwtProvider;

  @Test
  void 지도_매장_조회_end_to_end_dealsOnly_false() throws Exception {
    // given — 고객
    Customer customer = customerRepository.save(newCustomer());
    String token = jwtProvider.issueAccessToken(customer.getId(), Role.CUSTOMER);

    // given — 반경 내 OPEN 매장 (오늘 영업)
    Seller seller = sellerRepository.save(newSeller());
    Store store =
        storeRepository.save(newStore(seller, "지도테스트매장", NEAR_LAT, NEAR_LNG, OperationStatus.OPEN));
    storeBusinessHourRepository.save(
        StoreBusinessHour.builder()
            .store(store)
            .dayOfWeek(LocalDate.now().getDayOfWeek())
            .openTime(LocalTime.of(9, 0))
            .closeTime(LocalTime.of(21, 0))
            .build());

    // when
    MvcResult result =
        mockMvc
            .perform(
                get("/api/v1/stores/map")
                    .param("latitude", String.valueOf(ORIGIN_LAT))
                    .param("longitude", String.valueOf(ORIGIN_LNG))
                    .param("radiusKm", "3")
                    .param("dealsOnly", "false")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();

    // then
    JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    assertThat(data.isArray()).isTrue();

    boolean found = false;
    for (JsonNode item : data) {
      if (item.path("id").asLong() == store.getId()) {
        found = true;
        assertThat(item.path("name").asText()).isEqualTo("지도테스트매장");
        assertThat(item.path("distanceKm").asDouble()).isLessThan(1.0); // 약 0.28km
        assertThat(item.path("latitude").asDouble()).isGreaterThan(37.0);
        assertThat(item.path("longitude").asDouble()).isGreaterThan(126.0);
        assertThat(item.path("activeDealCount").asInt()).isZero(); // 떨이 없음
        break;
      }
    }
    assertThat(found).isTrue();
  }

  @Test
  void dealsOnly_true_떨이있는_매장만_반환() throws Exception {
    // given — 고객
    Customer customer = customerRepository.save(newCustomer());
    String token = jwtProvider.issueAccessToken(customer.getId(), Role.CUSTOMER);

    Seller seller = sellerRepository.save(newSeller());

    // 떨이 있는 매장
    Store dealStore =
        storeRepository.save(newStore(seller, "떨이매장", NEAR_LAT, NEAR_LNG, OperationStatus.OPEN));
    storeBusinessHourRepository.save(
        StoreBusinessHour.builder()
            .store(dealStore)
            .dayOfWeek(LocalDate.now().getDayOfWeek())
            .openTime(LocalTime.of(9, 0))
            .closeTime(LocalTime.of(21, 0))
            .build());
    clearanceItemRepository.save(
        ClearanceItem.builder()
            .store(dealStore)
            .name("크로아상 떨이")
            .regularPrice(new BigDecimal("4500"))
            .salePrice(new BigDecimal("3000"))
            .totalQuantity(5)
            .pickupStartAt(LocalDateTime.now().minusHours(1))
            .pickupEndAt(LocalDateTime.now().plusHours(3))
            .build());

    // when
    MvcResult result =
        mockMvc
            .perform(
                get("/api/v1/stores/map")
                    .param("latitude", String.valueOf(ORIGIN_LAT))
                    .param("longitude", String.valueOf(ORIGIN_LNG))
                    .param("radiusKm", "3")
                    .param("dealsOnly", "true")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();

    // then — dealStore 가 결과에 포함
    JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    assertThat(data.isArray()).isTrue();

    boolean found = false;
    for (JsonNode item : data) {
      if (item.path("id").asLong() == dealStore.getId()) {
        found = true;
        assertThat(item.path("activeDealCount").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(item.path("maxDiscountRate").asInt()).isGreaterThan(0); // 할인율 있음
        break;
      }
    }
    assertThat(found).isTrue();
  }

  @Test
  void 미인증_요청_401() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/stores/map")
                .param("latitude", String.valueOf(ORIGIN_LAT))
                .param("longitude", String.valueOf(ORIGIN_LNG))
                .param("radiusKm", "3")
                .param("dealsOnly", "false"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void radiusKm_잘못된_값_400() throws Exception {
    Customer customer = customerRepository.save(newCustomer());
    String token = jwtProvider.issueAccessToken(customer.getId(), Role.CUSTOMER);

    mockMvc
        .perform(
            get("/api/v1/stores/map")
                .param("latitude", String.valueOf(ORIGIN_LAT))
                .param("longitude", String.valueOf(ORIGIN_LNG))
                .param("radiusKm", "2")
                .param("dealsOnly", "false")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
  }

  // ── helper ───────────────────────────────────────────────────────────────────────────────────

  private Customer newCustomer() {
    return Customer.builder()
        .email("customer_map_" + System.nanoTime() + "@test.com")
        .passwordHash("x")
        .nickname("지도테스트고객")
        .build();
  }

  private Seller newSeller() {
    return Seller.builder()
        .email("seller_map_" + System.nanoTime() + "@test.com")
        .passwordHash("x")
        .ownerName("지도테스트사장")
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
