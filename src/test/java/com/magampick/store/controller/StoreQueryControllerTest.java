package com.magampick.store.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
import com.magampick.store.dto.StoreListItemResponse;
import com.magampick.store.dto.StoreListResponse;
import com.magampick.store.dto.StoreSort;
import com.magampick.store.service.StoreQueryService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StoreQueryController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class StoreQueryControllerTest {

  @Autowired MockMvc mockMvc;
  @MockitoBean StoreQueryService storeQueryService;
  @MockitoBean JwtProvider jwtProvider;

  private static final CustomUserDetails CUSTOMER = new CustomUserDetails(1L, Role.CUSTOMER);
  private static final CustomUserDetails SELLER = new CustomUserDetails(2L, Role.SELLER);

  // ── 200 정상 응답 ───────────────────────────────────────────────────────────────────────────────

  @Test
  void 고객_인증_후_목록_조회_200() throws Exception {
    StoreListResponse response =
        new StoreListResponse(
            List.of(new StoreListItemResponse(10L, "동네빵집", "/img/bread.jpg", 1.2, 4.5, 2, true)),
            0,
            20,
            false,
            1L,
            1L);
    given(storeQueryService.getStores(eq(1L), any(StoreSort.class), anyInt(), anyInt()))
        .willReturn(response);

    mockMvc
        .perform(get("/api/v1/stores").with(user(CUSTOMER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.items").isArray())
        .andExpect(jsonPath("$.data.items[0].id").value(10))
        .andExpect(jsonPath("$.data.items[0].name").value("동네빵집"))
        .andExpect(jsonPath("$.data.items[0].distanceKm").value(1.2))
        .andExpect(jsonPath("$.data.items[0].rating").value(4.5))
        .andExpect(jsonPath("$.data.items[0].activeDealCount").value(2))
        .andExpect(jsonPath("$.data.items[0].isFavorite").value(true))
        .andExpect(jsonPath("$.data.total").value(1))
        .andExpect(jsonPath("$.data.dealStoreCount").value(1))
        .andExpect(jsonPath("$.data.hasNext").value(false));
  }

  @Test
  void sort_파라미터_recommended_전달() throws Exception {
    given(storeQueryService.getStores(eq(1L), eq(StoreSort.RECOMMENDED), anyInt(), anyInt()))
        .willReturn(emptyResponse());

    mockMvc
        .perform(get("/api/v1/stores?sort=recommended").with(user(CUSTOMER)))
        .andExpect(status().isOk());
  }

  @Test
  void sort_파라미터_distance_전달() throws Exception {
    given(storeQueryService.getStores(eq(1L), eq(StoreSort.DISTANCE), anyInt(), anyInt()))
        .willReturn(emptyResponse());

    mockMvc
        .perform(get("/api/v1/stores?sort=distance").with(user(CUSTOMER)))
        .andExpect(status().isOk());
  }

  @Test
  void sort_파라미터_잘못된_값은_recommended_로_fallback() throws Exception {
    given(storeQueryService.getStores(eq(1L), eq(StoreSort.RECOMMENDED), anyInt(), anyInt()))
        .willReturn(emptyResponse());

    mockMvc
        .perform(get("/api/v1/stores?sort=invalid_sort").with(user(CUSTOMER)))
        .andExpect(status().isOk());
  }

  // ── 인증/인가 오류 ─────────────────────────────────────────────────────────────────────────────

  @Test
  void 미인증_요청_401() throws Exception {
    mockMvc.perform(get("/api/v1/stores")).andExpect(status().isUnauthorized());
  }

  @Test
  void 사장_인증_403() throws Exception {
    mockMvc.perform(get("/api/v1/stores").with(user(SELLER))).andExpect(status().isForbidden());
  }

  // ── 비즈니스 오류 ──────────────────────────────────────────────────────────────────────────────

  @Test
  void 기본주소지_없으면_400() throws Exception {
    given(storeQueryService.getStores(eq(1L), any(StoreSort.class), anyInt(), anyInt()))
        .willThrow(new BusinessException(AddressErrorCode.DEFAULT_ADDRESS_REQUIRED));

    mockMvc
        .perform(get("/api/v1/stores").with(user(CUSTOMER)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("DEFAULT_ADDRESS_REQUIRED"));
  }

  // ── helper ───────────────────────────────────────────────────────────────────────────────────

  private StoreListResponse emptyResponse() {
    return new StoreListResponse(List.of(), 0, 20, false, 0L, 0L);
  }
}
