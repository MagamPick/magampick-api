package com.magampick.store.controller;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.magampick.clearance.service.StoreDealQueryService;
import com.magampick.global.exception.BusinessException;
import com.magampick.global.exception.CommonErrorCode;
import com.magampick.global.security.CustomUserDetails;
import com.magampick.global.security.JwtAccessDeniedHandler;
import com.magampick.global.security.JwtAuthenticationEntryPoint;
import com.magampick.global.security.JwtProvider;
import com.magampick.global.security.Role;
import com.magampick.global.security.SecurityConfig;
import com.magampick.store.dto.MapStoreResponse;
import com.magampick.store.service.StoreDetailQueryService;
import com.magampick.store.service.StoreMapQueryService;
import com.magampick.store.service.StoreMenuQueryService;
import com.magampick.store.service.StoreNeighborhoodQueryService;
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
class StoreMapQueryControllerTest {

  @Autowired MockMvc mockMvc;
  @MockitoBean StoreQueryService storeQueryService;
  @MockitoBean StoreDetailQueryService storeDetailQueryService;
  @MockitoBean StoreDealQueryService storeDealQueryService;
  @MockitoBean StoreMenuQueryService storeMenuQueryService;
  @MockitoBean StoreMapQueryService storeMapQueryService;
  @MockitoBean StoreNeighborhoodQueryService storeNeighborhoodQueryService;
  @MockitoBean JwtProvider jwtProvider;

  private static final CustomUserDetails CUSTOMER = new CustomUserDetails(1L, Role.CUSTOMER);
  private static final CustomUserDetails SELLER = new CustomUserDetails(2L, Role.SELLER);

  private static final String MAP_URL =
      "/api/v1/stores/map?latitude=37.5665&longitude=126.9780&radiusKm=3&dealsOnly=false";

  // ── 200 정상 응답 ───────────────────────────────────────────────────────────────────────────────

  @Test
  void 고객_인증_후_지도_매장_조회_200() throws Exception {
    MapStoreResponse response =
        new MapStoreResponse(10L, "동네빵집", "/img/bread.jpg", 37.57, 126.98, 1.2, 4.5, 2, 35);
    given(storeMapQueryService.getMapStores(anyDouble(), anyDouble(), anyInt(), anyBoolean()))
        .willReturn(List.of(response));

    mockMvc
        .perform(get(MAP_URL).with(user(CUSTOMER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data[0].id").value(10))
        .andExpect(jsonPath("$.data[0].name").value("동네빵집"))
        .andExpect(jsonPath("$.data[0].latitude").value(37.57))
        .andExpect(jsonPath("$.data[0].longitude").value(126.98))
        .andExpect(jsonPath("$.data[0].distanceKm").value(1.2))
        .andExpect(jsonPath("$.data[0].rating").value(4.5))
        .andExpect(jsonPath("$.data[0].activeDealCount").value(2))
        .andExpect(jsonPath("$.data[0].maxDiscountRate").value(35));
  }

  @Test
  void dealsOnly_true_파라미터_전달() throws Exception {
    given(storeMapQueryService.getMapStores(eq(37.5665), eq(126.9780), eq(3), eq(true)))
        .willReturn(List.of());

    mockMvc
        .perform(
            get("/api/v1/stores/map?latitude=37.5665&longitude=126.9780&radiusKm=3&dealsOnly=true")
                .with(user(CUSTOMER)))
        .andExpect(status().isOk());
  }

  @Test
  void radiusKm_1_파라미터_전달() throws Exception {
    given(storeMapQueryService.getMapStores(anyDouble(), anyDouble(), eq(1), anyBoolean()))
        .willReturn(List.of());

    mockMvc
        .perform(
            get("/api/v1/stores/map?latitude=37.5665&longitude=126.9780&radiusKm=1&dealsOnly=false")
                .with(user(CUSTOMER)))
        .andExpect(status().isOk());
  }

  @Test
  void 빈_결과_200() throws Exception {
    given(storeMapQueryService.getMapStores(anyDouble(), anyDouble(), anyInt(), anyBoolean()))
        .willReturn(List.of());

    mockMvc
        .perform(get(MAP_URL).with(user(CUSTOMER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data").isEmpty());
  }

  // ── 인증/인가 오류 ─────────────────────────────────────────────────────────────────────────────

  @Test
  void 미인증_요청_401() throws Exception {
    mockMvc.perform(get(MAP_URL)).andExpect(status().isUnauthorized());
  }

  @Test
  void 사장_인증_403() throws Exception {
    mockMvc.perform(get(MAP_URL).with(user(SELLER))).andExpect(status().isForbidden());
  }

  // ── radiusKm 검증 ─────────────────────────────────────────────────────────────────────────────

  @Test
  void radiusKm_2_서비스_예외_400() throws Exception {
    given(storeMapQueryService.getMapStores(anyDouble(), anyDouble(), eq(2), anyBoolean()))
        .willThrow(new BusinessException(CommonErrorCode.INVALID_INPUT));

    mockMvc
        .perform(
            get("/api/v1/stores/map?latitude=37.5665&longitude=126.9780&radiusKm=2&dealsOnly=false")
                .with(user(CUSTOMER)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
  }
}
