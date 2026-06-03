package com.magampick.favorite.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.magampick.favorite.dto.FavoriteAddRequest;
import com.magampick.favorite.fixture.FavoriteFixture;
import com.magampick.favorite.service.FavoriteService;
import com.magampick.global.exception.BusinessException;
import com.magampick.global.response.PageResponse;
import com.magampick.global.security.CustomUserDetails;
import com.magampick.global.security.JwtAccessDeniedHandler;
import com.magampick.global.security.JwtAuthenticationEntryPoint;
import com.magampick.global.security.JwtProvider;
import com.magampick.global.security.Role;
import com.magampick.global.security.SecurityConfig;
import com.magampick.store.exception.StoreErrorCode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(FavoriteController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class FavoriteControllerTest {

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @MockitoBean FavoriteService favoriteService;
  @MockitoBean JwtProvider jwtProvider;

  private static final CustomUserDetails CUSTOMER_USER = new CustomUserDetails(1L, Role.CUSTOMER);
  private static final CustomUserDetails SELLER_USER = new CustomUserDetails(2L, Role.SELLER);

  // ── POST /api/v1/customers/me/favorites ──────────────────────────────────────

  @Test
  void POST_favorites_201_등록_성공() throws Exception {
    given(favoriteService.addFavorite(eq(1L), eq(10L)))
        .willReturn(FavoriteFixture.aAddResponse(10L));

    mockMvc
        .perform(
            post("/api/v1/customers/me/favorites")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new FavoriteAddRequest(10L)))
                .with(user(CUSTOMER_USER)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.storeId").value(10));
  }

  @Test
  void POST_favorites_401_미인증() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/customers/me/favorites")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new FavoriteAddRequest(10L))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void POST_favorites_403_셀러_접근_거부() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/customers/me/favorites")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new FavoriteAddRequest(10L)))
                .with(user(SELLER_USER)))
        .andExpect(status().isForbidden());
  }

  @Test
  void POST_favorites_400_storeId_누락() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/customers/me/favorites")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(user(CUSTOMER_USER)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
  }

  @Test
  void POST_favorites_404_매장_없음() throws Exception {
    given(favoriteService.addFavorite(any(), any()))
        .willThrow(new BusinessException(StoreErrorCode.STORE_NOT_FOUND));

    mockMvc
        .perform(
            post("/api/v1/customers/me/favorites")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new FavoriteAddRequest(10L)))
                .with(user(CUSTOMER_USER)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("STORE_NOT_FOUND"));
  }

  // ── DELETE /api/v1/customers/me/favorites/{storeId} ──────────────────────────

  @Test
  void DELETE_favorites_storeId_204_해제_성공() throws Exception {
    willDoNothing().given(favoriteService).removeFavorite(1L, 10L);

    mockMvc
        .perform(delete("/api/v1/customers/me/favorites/10").with(user(CUSTOMER_USER)))
        .andExpect(status().isNoContent());
  }

  @Test
  void DELETE_favorites_storeId_401_미인증() throws Exception {
    mockMvc
        .perform(delete("/api/v1/customers/me/favorites/10"))
        .andExpect(status().isUnauthorized());
  }

  // ── GET /api/v1/customers/me/favorites ───────────────────────────────────────

  @Test
  void GET_favorites_200_목록_조회() throws Exception {
    PageResponse<com.magampick.favorite.dto.FavoriteStoreResponse> page =
        new PageResponse<>(
            List.of(FavoriteFixture.aStoreResponse(10L)), 0, 20, 1L, 1, false, false);
    given(favoriteService.getFavorites(eq(1L), any())).willReturn(page);

    mockMvc
        .perform(get("/api/v1/customers/me/favorites").with(user(CUSTOMER_USER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content[0].storeId").value(10))
        .andExpect(jsonPath("$.data.totalCount").value(1));
  }

  @Test
  void GET_favorites_401_미인증() throws Exception {
    mockMvc.perform(get("/api/v1/customers/me/favorites")).andExpect(status().isUnauthorized());
  }
}
