package com.magampick.favorite;

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
import com.magampick.favorite.domain.Favorite;
import com.magampick.favorite.repository.FavoriteRepository;
import com.magampick.global.common.GeometryUtil;
import com.magampick.global.security.JwtProvider;
import com.magampick.global.security.Role;
import com.magampick.seller.domain.Seller;
import com.magampick.seller.repository.SellerRepository;
import com.magampick.store.domain.OperationStatus;
import com.magampick.store.domain.Store;
import com.magampick.store.repository.StoreRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 단골 목록 조회 end-to-end 통합 테스트. 기본 주소지 + 단골 다건(일부 활성 떨이) → 정렬·통계·거리 검증. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class FavoriteIntegrationTest {

  // origin: 서울시청
  private static final double ORIGIN_LAT = 37.5665;
  private static final double ORIGIN_LNG = 126.9780;
  // ~280m 거리
  private static final double NEAR_LAT = 37.5685;
  private static final double NEAR_LNG = 126.9800;
  // ~1km 거리
  private static final double MID_LAT = 37.5600;
  private static final double MID_LNG = 126.9720;

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @Autowired CustomerRepository customerRepository;
  @Autowired AddressRepository addressRepository;
  @Autowired SellerRepository sellerRepository;
  @Autowired StoreRepository storeRepository;
  @Autowired ClearanceItemRepository clearanceItemRepository;
  @Autowired FavoriteRepository favoriteRepository;
  @Autowired JwtProvider jwtProvider;

  @Test
  void 단골_목록_활성떨이_우선_통계_거리_end_to_end() throws Exception {
    // ── 1. 소비자 + 기본 주소지 설정 ──────────────────────────────────────────
    Customer customer = customerRepository.save(newCustomer());
    addressRepository.save(defaultAddress(customer));

    // ── 2. 매장 2개 생성 ────────────────────────────────────────────────────────
    Seller seller = sellerRepository.save(newSeller());
    // storeA: 활성 떨이 있음 (나중 등록)
    Store storeA = storeRepository.save(newStore(seller, "활성떨이매장", NEAR_LAT, NEAR_LNG));
    // storeB: 활성 떨이 없음 (먼저 등록)
    Store storeB = storeRepository.save(newStore(seller, "일반매장", MID_LAT, MID_LNG));

    // ── 3. 떨이 등록 (storeA 에만) ──────────────────────────────────────────────
    clearanceItemRepository.save(openDeal(storeA));
    clearanceItemRepository.save(openDeal(storeA));

    // ── 4. 단골 등록 (storeB 먼저, storeA 나중) ─────────────────────────────────
    favoriteRepository.save(Favorite.builder().customer(customer).store(storeB).build());
    favoriteRepository.save(Favorite.builder().customer(customer).store(storeA).build());

    // ── 5. API 호출 ───────────────────────────────────────────────────────────────
    String token = jwtProvider.issueAccessToken(customer.getId(), Role.CUSTOMER);

    MvcResult result =
        mockMvc
            .perform(
                get("/api/v1/customers/me/favorites")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();

    JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    JsonNode stores = root.path("stores");

    // ── 6. 검증 ────────────────────────────────────────────────────────────────
    // totalCount = 2
    assertThat(root.path("totalCount").asLong()).isEqualTo(2L);
    // totalActiveDealCount = 2 (storeA 활성 떨이 2개)
    assertThat(root.path("totalActiveDealCount").asLong()).isEqualTo(2L);
    // stores 크기 = 2
    assertThat(stores.size()).isEqualTo(2);
    // 첫 번째 = storeA (활성 떨이 있으므로 우선)
    assertThat(stores.get(0).path("name").asText()).isEqualTo("활성떨이매장");
    assertThat(stores.get(0).path("activeDealCount").asLong()).isEqualTo(2L);
    // 두 번째 = storeB (활성 떨이 없음)
    assertThat(stores.get(1).path("name").asText()).isEqualTo("일반매장");
    assertThat(stores.get(1).path("activeDealCount").asLong()).isZero();
    // 거리 필드 존재하고 양수
    assertThat(stores.get(0).path("distanceKm").asDouble()).isGreaterThan(0.0);
    assertThat(stores.get(1).path("distanceKm").asDouble()).isGreaterThan(0.0);
    // rating 필드 존재 (리뷰 없으면 0.0)
    assertThat(stores.get(0).path("rating").asDouble()).isGreaterThanOrEqualTo(0.0);
  }

  @Test
  void 기본주소지_없으면_400_DEFAULT_ADDRESS_REQUIRED() throws Exception {
    Customer customer = customerRepository.save(newCustomer());
    // 주소지 미등록
    String token = jwtProvider.issueAccessToken(customer.getId(), Role.CUSTOMER);

    mockMvc
        .perform(
            get("/api/v1/customers/me/favorites")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("DEFAULT_ADDRESS_REQUIRED"));
  }

  @Test
  void 단골_없으면_빈_목록_200() throws Exception {
    Customer customer = customerRepository.save(newCustomer());
    addressRepository.save(defaultAddress(customer));
    // 단골 미등록
    String token = jwtProvider.issueAccessToken(customer.getId(), Role.CUSTOMER);

    MvcResult result =
        mockMvc
            .perform(
                get("/api/v1/customers/me/favorites")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();

    JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    assertThat(root.path("stores").size()).isZero();
    assertThat(root.path("totalCount").asLong()).isZero();
    assertThat(root.path("totalActiveDealCount").asLong()).isZero();
  }

  @Test
  void 미인증_401() throws Exception {
    mockMvc.perform(get("/api/v1/customers/me/favorites")).andExpect(status().isUnauthorized());
  }

  // ── helper ───────────────────────────────────────────────────────────────────────────────────

  private Customer newCustomer() {
    return Customer.builder()
        .email("fav_integ_" + System.nanoTime() + "@test.com")
        .passwordHash("x")
        .nickname("테스트고객")
        .build();
  }

  private Seller newSeller() {
    return Seller.builder()
        .email("seller_fav_" + System.nanoTime() + "@test.com")
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

  private Store newStore(Seller seller, String name, double lat, double lng) {
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
        .operationStatus(OperationStatus.OPEN)
        .build();
  }

  private ClearanceItem openDeal(Store store) {
    return ClearanceItem.builder()
        .store(store)
        .name("마감할인상품")
        .regularPrice(new BigDecimal("5000"))
        .salePrice(new BigDecimal("3000"))
        .totalQuantity(5)
        .pickupStartAt(LocalDateTime.now().minusHours(1))
        .pickupEndAt(LocalDateTime.now().plusHours(2))
        .build();
  }
}
