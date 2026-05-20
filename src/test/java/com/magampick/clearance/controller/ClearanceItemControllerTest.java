package com.magampick.clearance.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.magampick.clearance.dto.ClearanceItemCreateRequest;
import com.magampick.clearance.exception.ClearanceItemErrorCode;
import com.magampick.clearance.fixture.ClearanceItemFixture;
import com.magampick.clearance.service.ClearanceItemService;
import com.magampick.global.exception.BusinessException;
import com.magampick.global.response.PageResponse;
import com.magampick.global.security.CustomUserDetails;
import com.magampick.global.security.JwtAccessDeniedHandler;
import com.magampick.global.security.JwtAuthenticationEntryPoint;
import com.magampick.global.security.JwtProvider;
import com.magampick.global.security.Role;
import com.magampick.global.security.SecurityConfig;
import com.magampick.store.exception.StoreErrorCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ClearanceItemController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class ClearanceItemControllerTest {

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @MockitoBean ClearanceItemService clearanceItemService;
  @MockitoBean JwtProvider jwtProvider;

  private static final CustomUserDetails SELLER_USER = new CustomUserDetails(1L, Role.SELLER);
  private static final CustomUserDetails CUSTOMER_USER = new CustomUserDetails(2L, Role.CUSTOMER);

  private String validCreateJson() throws Exception {
    LocalDateTime todayAt17 = LocalDate.now().atTime(17, 0);
    LocalDateTime todayAt21 = LocalDate.now().atTime(21, 0);
    return objectMapper.writeValueAsString(
        new ClearanceItemCreateRequest(100L, new BigDecimal("3000"), 5, todayAt17, todayAt21));
  }

  // ── POST /api/v1/seller/stores/{storeId}/clearance-items ──────────────────

  @Test
  void POST_clearance_items_201_성공() throws Exception {
    given(clearanceItemService.registerClearanceItem(eq(1L), eq(10L), any()))
        .willReturn(ClearanceItemFixture.aResponse(200L));

    mockMvc
        .perform(
            post("/api/v1/seller/stores/10/clearance-items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validCreateJson())
                .with(user(SELLER_USER)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.id").value(200))
        .andExpect(jsonPath("$.data.status").value("OPEN"));
  }

  @Test
  void POST_clearance_items_401_미인증() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/seller/stores/10/clearance-items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validCreateJson()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void POST_clearance_items_403_소비자_접근_거부() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/seller/stores/10/clearance-items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validCreateJson())
                .with(user(CUSTOMER_USER)))
        .andExpect(status().isForbidden());
  }

  @Test
  void POST_clearance_items_400_입력_검증_실패() throws Exception {
    // salePrice null
    String invalidJson =
        objectMapper.writeValueAsString(
            new ClearanceItemCreateRequest(
                100L, null, 5, LocalDate.now().atTime(17, 0), LocalDate.now().atTime(21, 0)));

    mockMvc
        .perform(
            post("/api/v1/seller/stores/10/clearance-items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson)
                .with(user(SELLER_USER)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
  }

  // ── GET /api/v1/seller/stores/{storeId}/clearance-items ───────────────────

  @Test
  void GET_clearance_items_200_목록() throws Exception {
    PageResponse<com.magampick.clearance.dto.ClearanceItemResponse> page =
        new PageResponse<>(
            List.of(ClearanceItemFixture.aResponse(200L)), 0, 20, 1L, 1, false, false);
    given(clearanceItemService.getMyClearanceItems(eq(1L), eq(10L), any())).willReturn(page);

    mockMvc
        .perform(get("/api/v1/seller/stores/10/clearance-items").with(user(SELLER_USER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content[0].id").value(200))
        .andExpect(jsonPath("$.data.totalCount").value(1));
  }

  // ── GET /api/v1/seller/stores/{storeId}/clearance-items/{id} ─────────────

  @Test
  void GET_clearance_items_id_200_상세() throws Exception {
    given(clearanceItemService.getMyClearanceItem(1L, 10L, 200L))
        .willReturn(ClearanceItemFixture.aResponse(200L));

    mockMvc
        .perform(get("/api/v1/seller/stores/10/clearance-items/200").with(user(SELLER_USER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(200));
  }

  @Test
  void GET_clearance_items_id_404_없음() throws Exception {
    given(clearanceItemService.getMyClearanceItem(1L, 10L, 200L))
        .willThrow(new BusinessException(ClearanceItemErrorCode.CLEARANCE_ITEM_NOT_FOUND));

    mockMvc
        .perform(get("/api/v1/seller/stores/10/clearance-items/200").with(user(SELLER_USER)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("CLEARANCE_ITEM_NOT_FOUND"));
  }

  @Test
  void GET_clearance_items_id_403_타인_매장() throws Exception {
    given(clearanceItemService.getMyClearanceItem(1L, 10L, 200L))
        .willThrow(new BusinessException(StoreErrorCode.STORE_ACCESS_DENIED));

    mockMvc
        .perform(get("/api/v1/seller/stores/10/clearance-items/200").with(user(SELLER_USER)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("STORE_ACCESS_DENIED"));
  }
}
