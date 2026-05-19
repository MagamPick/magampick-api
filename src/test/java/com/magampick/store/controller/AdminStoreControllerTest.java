package com.magampick.store.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.magampick.global.exception.BusinessException;
import com.magampick.global.response.PageResponse;
import com.magampick.global.security.CustomUserDetails;
import com.magampick.global.security.JwtAccessDeniedHandler;
import com.magampick.global.security.JwtAuthenticationEntryPoint;
import com.magampick.global.security.JwtProvider;
import com.magampick.global.security.Role;
import com.magampick.global.security.SecurityConfig;
import com.magampick.store.domain.StoreStatus;
import com.magampick.store.dto.StoreAdminResponse;
import com.magampick.store.dto.StoreRejectRequest;
import com.magampick.store.exception.StoreErrorCode;
import com.magampick.store.service.StoreService;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminStoreController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class AdminStoreControllerTest {

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @MockBean StoreService storeService;
  @MockBean JwtProvider jwtProvider;

  private static final CustomUserDetails ADMIN_USER = new CustomUserDetails(1L, Role.ADMIN);
  private static final CustomUserDetails SELLER_USER = new CustomUserDetails(2L, Role.SELLER);

  private StoreAdminResponse stubAdminResponse() {
    return new StoreAdminResponse(
        1L, "동네빵집", "서울 강남구 테헤란로 427", StoreStatus.PENDING, 10L, "홍길동", OffsetDateTime.now());
  }

  private PageResponse<StoreAdminResponse> stubPage(List<StoreAdminResponse> items) {
    org.springframework.data.domain.Page<StoreAdminResponse> page =
        new org.springframework.data.domain.PageImpl<>(items);
    return PageResponse.of(page);
  }

  // ── GET /api/v1/admin/stores ───────────────────────────────────────────────

  @Test
  void GET_admin_stores_200_페이지네이션() throws Exception {
    given(storeService.getStoresForAdmin(isNull(), any()))
        .willReturn(stubPage(List.of(stubAdminResponse())));

    mockMvc
        .perform(get("/api/v1/admin/stores").with(user(ADMIN_USER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content[0].name").value("동네빵집"));
  }

  @Test
  void GET_admin_stores_status_필터_200() throws Exception {
    given(storeService.getStoresForAdmin(eq(StoreStatus.PENDING), any()))
        .willReturn(stubPage(List.of(stubAdminResponse())));

    mockMvc
        .perform(get("/api/v1/admin/stores?status=PENDING").with(user(ADMIN_USER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content[0].status").value("PENDING"));
  }

  // ── PATCH /api/v1/admin/stores/{id}/approve ────────────────────────────────

  @Test
  void PATCH_approve_204_성공() throws Exception {
    willDoNothing().given(storeService).approveStore(1L);

    mockMvc
        .perform(patch("/api/v1/admin/stores/1/approve").with(user(ADMIN_USER)))
        .andExpect(status().isNoContent());
  }

  @Test
  void PATCH_approve_409_이미_심사됨() throws Exception {
    willThrow(new BusinessException(StoreErrorCode.STORE_ALREADY_REVIEWED))
        .given(storeService)
        .approveStore(1L);

    mockMvc
        .perform(patch("/api/v1/admin/stores/1/approve").with(user(ADMIN_USER)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("STORE_ALREADY_REVIEWED"));
  }

  // ── PATCH /api/v1/admin/stores/{id}/reject ─────────────────────────────────

  @Test
  void PATCH_reject_204_성공() throws Exception {
    willDoNothing().given(storeService).rejectStore(eq(1L), any());
    StoreRejectRequest req = new StoreRejectRequest("사업자번호 불일치");

    mockMvc
        .perform(
            patch("/api/v1/admin/stores/1/reject")
                .with(user(ADMIN_USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isNoContent());
  }

  // ── 인증/인가 경계 ─────────────────────────────────────────────────────────

  @Test
  void PATCH_approve_401_미인증() throws Exception {
    mockMvc.perform(patch("/api/v1/admin/stores/1/approve")).andExpect(status().isUnauthorized());
  }

  @Test
  void PATCH_approve_403_사장_접근_거부() throws Exception {
    mockMvc
        .perform(patch("/api/v1/admin/stores/1/approve").with(user(SELLER_USER)))
        .andExpect(status().isForbidden());
  }
}
