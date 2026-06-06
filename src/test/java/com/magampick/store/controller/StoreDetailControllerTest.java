package com.magampick.store.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.magampick.clearance.dto.StoreDealResponse;
import com.magampick.clearance.service.StoreDealQueryService;
import com.magampick.global.exception.BusinessException;
import com.magampick.global.security.CustomUserDetails;
import com.magampick.global.security.JwtAccessDeniedHandler;
import com.magampick.global.security.JwtAuthenticationEntryPoint;
import com.magampick.global.security.JwtProvider;
import com.magampick.global.security.Role;
import com.magampick.global.security.SecurityConfig;
import com.magampick.product.dto.StoreMenuItemResponse;
import com.magampick.store.domain.OperationStatus;
import com.magampick.store.dto.ConsumerStoreDetailResponse;
import com.magampick.store.dto.OperatingHourResponse;
import com.magampick.store.exception.StoreErrorCode;
import com.magampick.store.service.StoreDetailQueryService;
import com.magampick.store.service.StoreMenuQueryService;
import com.magampick.store.service.StoreQueryService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StoreQueryController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class StoreDetailControllerTest {

  @Autowired MockMvc mockMvc;
  @MockitoBean StoreQueryService storeQueryService;
  @MockitoBean StoreDetailQueryService storeDetailQueryService;
  @MockitoBean StoreDealQueryService storeDealQueryService;
  @MockitoBean StoreMenuQueryService storeMenuQueryService;
  @MockitoBean JwtProvider jwtProvider;

  private static final CustomUserDetails CUSTOMER = new CustomUserDetails(1L, Role.CUSTOMER);
  private static final CustomUserDetails SELLER = new CustomUserDetails(2L, Role.SELLER);
  private static final Long STORE_ID = 10L;

  // ── 매장 상세 (ROLE_CUSTOMER 필요) ─────────────────────────────────────────────────────────────

  @Test
  void 고객_인증_매장상세_200() throws Exception {
    given(storeDetailQueryService.getDetail(eq(STORE_ID), eq(1L))).willReturn(stubDetailResponse());

    mockMvc
        .perform(get("/api/v1/stores/{id}", STORE_ID).with(user(CUSTOMER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(STORE_ID))
        .andExpect(jsonPath("$.data.name").value("동네빵집"))
        .andExpect(jsonPath("$.data.businessStatus").value("OPEN"))
        .andExpect(jsonPath("$.data.rating").value(4.2))
        .andExpect(jsonPath("$.data.reviewCount").value(8))
        .andExpect(jsonPath("$.data.distanceKm").value(1.5))
        .andExpect(jsonPath("$.data.isFavorite").value(true))
        .andExpect(jsonPath("$.data.operatingHours").isArray())
        .andExpect(jsonPath("$.data.operatingHours.length()").value(7));
  }

  @Test
  void 미인증_매장상세_401() throws Exception {
    mockMvc.perform(get("/api/v1/stores/{id}", STORE_ID)).andExpect(status().isUnauthorized());
  }

  @Test
  void 사장_인증_매장상세_403() throws Exception {
    mockMvc
        .perform(get("/api/v1/stores/{id}", STORE_ID).with(user(SELLER)))
        .andExpect(status().isForbidden());
  }

  @Test
  void 매장_없음_404() throws Exception {
    given(storeDetailQueryService.getDetail(eq(STORE_ID), eq(1L)))
        .willThrow(new BusinessException(StoreErrorCode.STORE_NOT_FOUND));

    mockMvc
        .perform(get("/api/v1/stores/{id}", STORE_ID).with(user(CUSTOMER)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("STORE_NOT_FOUND"));
  }

  // ── 마감할인 탭 (public) ────────────────────────────────────────────────────────────────────────

  @Test
  void 마감할인_탭_인증_없이_200() throws Exception {
    given(storeDealQueryService.getActiveDeals(STORE_ID))
        .willReturn(
            List.of(
                new StoreDealResponse(
                    1L,
                    "크로아상",
                    "/img/c.jpg",
                    40,
                    new BigDecimal("5000"),
                    new BigDecimal("3000"),
                    LocalDateTime.now().plusHours(2),
                    3)));

    mockMvc
        .perform(get("/api/v1/stores/{id}/clearance-items", STORE_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data[0].discountRate").value(40))
        .andExpect(jsonPath("$.data[0].stockLeft").value(3));
  }

  @Test
  void 마감할인_탭_빈_리스트_200() throws Exception {
    given(storeDealQueryService.getActiveDeals(STORE_ID)).willReturn(List.of());

    mockMvc
        .perform(get("/api/v1/stores/{id}/clearance-items", STORE_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data").isEmpty());
  }

  // ── 메뉴 탭 (public) ────────────────────────────────────────────────────────────────────────────

  @Test
  void 메뉴_탭_인증_없이_200() throws Exception {
    given(storeMenuQueryService.getMenu(STORE_ID))
        .willReturn(
            List.of(
                new StoreMenuItemResponse(
                    1L, "크로아상", "/img/c.jpg", new BigDecimal("3500"), "베이커리")));

    mockMvc
        .perform(get("/api/v1/stores/{id}/menu", STORE_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data[0].name").value("크로아상"))
        .andExpect(jsonPath("$.data[0].category").value("베이커리"));
  }

  @Test
  void 메뉴_탭_빈_리스트_200() throws Exception {
    given(storeMenuQueryService.getMenu(STORE_ID)).willReturn(List.of());

    mockMvc
        .perform(get("/api/v1/stores/{id}/menu", STORE_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isEmpty());
  }

  // ── helpers ───────────────────────────────────────────────────────────────────────────────────

  private ConsumerStoreDetailResponse stubDetailResponse() {
    List<OperatingHourResponse> hours =
        List.of(
            new OperatingHourResponse("월", "09:00", "21:00", false),
            new OperatingHourResponse("화", null, null, true),
            new OperatingHourResponse("수", "09:00", "21:00", false),
            new OperatingHourResponse("목", null, null, true),
            new OperatingHourResponse("금", "09:00", "21:00", false),
            new OperatingHourResponse("토", null, null, true),
            new OperatingHourResponse("일", null, null, true));

    return new ConsumerStoreDetailResponse(
        STORE_ID,
        "동네빵집",
        "/img/bread.jpg",
        OperationStatus.OPEN,
        "21:00",
        4.2,
        8L,
        1.5,
        true,
        "서울시 중구 테스트로 1",
        "02-1234-5678",
        "1234567890",
        hours,
        37.5685,
        126.9800);
  }
}
