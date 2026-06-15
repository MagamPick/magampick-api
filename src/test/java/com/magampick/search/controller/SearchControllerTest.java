package com.magampick.search.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.magampick.address.exception.AddressErrorCode;
import com.magampick.global.exception.BusinessException;
import com.magampick.global.security.CustomUserDetails;
import com.magampick.global.security.JwtAccessDeniedHandler;
import com.magampick.global.security.JwtAuthenticationEntryPoint;
import com.magampick.global.security.JwtProvider;
import com.magampick.global.security.Role;
import com.magampick.global.security.SecurityConfig;
import com.magampick.search.dto.SearchProductItemResponse.DealSearchItem;
import com.magampick.search.dto.SearchProductItemResponse.MenuSearchItem;
import com.magampick.search.dto.SearchResultResponse;
import com.magampick.search.dto.SearchSuggestionResponse;
import com.magampick.search.dto.SuggestionKind;
import com.magampick.search.service.SearchQueryService;
import com.magampick.store.dto.StoreListItemResponse;
import com.magampick.store.dto.StoreSort;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SearchController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class SearchControllerTest {

  @Autowired MockMvc mockMvc;
  @MockitoBean SearchQueryService searchQueryService;
  @MockitoBean JwtProvider jwtProvider;

  private static final CustomUserDetails CUSTOMER = new CustomUserDetails(1L, Role.CUSTOMER);
  private static final CustomUserDetails SELLER = new CustomUserDetails(2L, Role.SELLER);

  // ── GET /api/v1/search — 200 정상 ──────────────────────────────────────────────────────────

  @Test
  void 검색_200_stores_products_포함() throws Exception {
    StoreListItemResponse store = new StoreListItemResponse(10L, "빵집", null, 1.2, 4.5, 2, true);
    DealSearchItem deal =
        new DealSearchItem(
            20L, 10L, "빵집", "크로아상", null, new BigDecimal("5000"), new BigDecimal("3500"), 30);
    MenuSearchItem menu = new MenuSearchItem(30L, 10L, "빵집", "아메리카노", null, new BigDecimal("4500"));
    SearchResultResponse response = new SearchResultResponse(List.of(store), List.of(deal, menu));
    given(searchQueryService.search(eq(1L), anyString(), any(StoreSort.class)))
        .willReturn(response);

    mockMvc
        .perform(get("/api/v1/search?q=빵집").with(user(CUSTOMER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.stores").isArray())
        .andExpect(jsonPath("$.data.stores[0].id").value(10))
        .andExpect(jsonPath("$.data.stores[0].name").value("빵집"))
        .andExpect(jsonPath("$.data.products").isArray())
        .andExpect(jsonPath("$.data.products[0].kind").value("deal"))
        .andExpect(jsonPath("$.data.products[0].id").value(20))
        .andExpect(jsonPath("$.data.products[0].discountRate").value(30))
        .andExpect(jsonPath("$.data.products[1].kind").value("menu"))
        .andExpect(jsonPath("$.data.products[1].id").value(30))
        .andExpect(jsonPath("$.data.products[1].price").value(4500));
  }

  @Test
  void 검색_deal_응답에_distance_rating_미포함() throws Exception {
    DealSearchItem deal =
        new DealSearchItem(
            20L, 10L, "빵집", "크로아상", null, new BigDecimal("5000"), new BigDecimal("3500"), 30);
    given(searchQueryService.search(eq(1L), anyString(), any(StoreSort.class)))
        .willReturn(new SearchResultResponse(List.of(), List.of(deal)));

    mockMvc
        .perform(get("/api/v1/search?q=크로아상").with(user(CUSTOMER)))
        .andExpect(status().isOk())
        // distance 필드가 없어야 함
        .andExpect(jsonPath("$.data.products[0].distanceKm").doesNotExist())
        .andExpect(jsonPath("$.data.products[0].storeDistanceKm").doesNotExist())
        .andExpect(jsonPath("$.data.products[0].storeRating").doesNotExist());
  }

  @Test
  void 검색_sort_기본값_recommended() throws Exception {
    given(searchQueryService.search(eq(1L), anyString(), eq(StoreSort.RECOMMENDED)))
        .willReturn(new SearchResultResponse(List.of(), List.of()));

    mockMvc.perform(get("/api/v1/search?q=빵집").with(user(CUSTOMER))).andExpect(status().isOk());
  }

  @Test
  void 검색_sort_distance_전달() throws Exception {
    given(searchQueryService.search(eq(1L), anyString(), eq(StoreSort.DISTANCE)))
        .willReturn(new SearchResultResponse(List.of(), List.of()));

    mockMvc
        .perform(get("/api/v1/search?q=빵집&sort=distance").with(user(CUSTOMER)))
        .andExpect(status().isOk());
  }

  @Test
  void 검색_빈_q_200_빈결과() throws Exception {
    given(searchQueryService.search(eq(1L), eq(""), any(StoreSort.class)))
        .willReturn(new SearchResultResponse(List.of(), List.of()));

    mockMvc
        .perform(get("/api/v1/search?q=").with(user(CUSTOMER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.stores").isEmpty())
        .andExpect(jsonPath("$.data.products").isEmpty());
  }

  // ── 인증/인가 ─────────────────────────────────────────────────────────────────────────────────

  @Test
  void 검색_미인증_401() throws Exception {
    mockMvc.perform(get("/api/v1/search?q=빵")).andExpect(status().isUnauthorized());
  }

  @Test
  void 검색_사장_403() throws Exception {
    mockMvc.perform(get("/api/v1/search?q=빵").with(user(SELLER))).andExpect(status().isForbidden());
  }

  @Test
  void 검색_기본주소지_없음_400() throws Exception {
    given(searchQueryService.search(eq(1L), anyString(), any(StoreSort.class)))
        .willThrow(new BusinessException(AddressErrorCode.DEFAULT_ADDRESS_REQUIRED));

    mockMvc
        .perform(get("/api/v1/search?q=빵집").with(user(CUSTOMER)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("DEFAULT_ADDRESS_REQUIRED"));
  }

  // ── GET /api/v1/search/autocomplete ───────────────────────────────────────────────────────

  @Test
  void 자동완성_200_제안_목록() throws Exception {
    List<SearchSuggestionResponse> suggestions =
        List.of(
            new SearchSuggestionResponse(SuggestionKind.STORE, "빵집"),
            new SearchSuggestionResponse(SuggestionKind.PRODUCT, "크로아상"));
    given(searchQueryService.autocomplete(eq(1L), anyString())).willReturn(suggestions);

    mockMvc
        .perform(get("/api/v1/search/autocomplete?q=빵").with(user(CUSTOMER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data[0].kind").value("store"))
        .andExpect(jsonPath("$.data[0].text").value("빵집"))
        .andExpect(jsonPath("$.data[1].kind").value("product"))
        .andExpect(jsonPath("$.data[1].text").value("크로아상"));
  }

  @Test
  void 자동완성_kind_소문자_직렬화() throws Exception {
    given(searchQueryService.autocomplete(eq(1L), anyString()))
        .willReturn(
            List.of(
                new SearchSuggestionResponse(SuggestionKind.STORE, "빵집"),
                new SearchSuggestionResponse(SuggestionKind.PRODUCT, "크로아상")));

    mockMvc
        .perform(get("/api/v1/search/autocomplete?q=빵").with(user(CUSTOMER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].kind").value("store"))
        .andExpect(jsonPath("$.data[1].kind").value("product"));
  }

  @Test
  void 자동완성_미인증_401() throws Exception {
    mockMvc.perform(get("/api/v1/search/autocomplete?q=빵")).andExpect(status().isUnauthorized());
  }

  @Test
  void 자동완성_사장_403() throws Exception {
    mockMvc
        .perform(get("/api/v1/search/autocomplete?q=빵").with(user(SELLER)))
        .andExpect(status().isForbidden());
  }

  @Test
  void 자동완성_기본주소지_없음_400() throws Exception {
    given(searchQueryService.autocomplete(eq(1L), anyString()))
        .willThrow(new BusinessException(AddressErrorCode.DEFAULT_ADDRESS_REQUIRED));

    mockMvc
        .perform(get("/api/v1/search/autocomplete?q=빵").with(user(CUSTOMER)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("DEFAULT_ADDRESS_REQUIRED"));
  }
}
