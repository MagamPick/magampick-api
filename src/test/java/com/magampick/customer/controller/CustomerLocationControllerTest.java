package com.magampick.customer.controller;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.magampick.customer.dto.CustomerLocationResponse;
import com.magampick.customer.dto.CustomerLocationUpdateRequest;
import com.magampick.customer.service.CustomerLocationService;
import com.magampick.global.security.CustomUserDetails;
import com.magampick.global.security.JwtAccessDeniedHandler;
import com.magampick.global.security.JwtAuthenticationEntryPoint;
import com.magampick.global.security.JwtProvider;
import com.magampick.global.security.Role;
import com.magampick.global.security.SecurityConfig;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CustomerLocationController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class CustomerLocationControllerTest {

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @MockitoBean CustomerLocationService customerLocationService;
  @MockitoBean JwtProvider jwtProvider;

  private static final CustomUserDetails CUSTOMER_USER = new CustomUserDetails(1L, Role.CUSTOMER);
  private static final CustomUserDetails SELLER_USER = new CustomUserDetails(2L, Role.SELLER);

  // ── PUT /api/v1/customers/me/location ─────────────────────────────────────

  @Test
  void PUT_위치_갱신_성공_200() throws Exception {
    // given
    LocalDateTime now = LocalDateTime.now();
    CustomerLocationResponse response = new CustomerLocationResponse(37.5665, 126.9780, now);
    given(customerLocationService.updateLocation(eq(1L), anyDouble(), anyDouble()))
        .willReturn(response);

    // when / then
    mockMvc
        .perform(
            put("/api/v1/customers/me/location")
                .with(user(CUSTOMER_USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new CustomerLocationUpdateRequest(37.5665, 126.9780))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.latitude").value(37.5665))
        .andExpect(jsonPath("$.data.longitude").value(126.9780));
  }

  @Test
  void PUT_위도_범위초과_400() throws Exception {
    mockMvc
        .perform(
            put("/api/v1/customers/me/location")
                .with(user(CUSTOMER_USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new CustomerLocationUpdateRequest(91.0, 126.9780))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
  }

  @Test
  void PUT_경도_범위초과_400() throws Exception {
    mockMvc
        .perform(
            put("/api/v1/customers/me/location")
                .with(user(CUSTOMER_USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new CustomerLocationUpdateRequest(37.5665, 181.0))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
  }

  @Test
  void PUT_미인증_401() throws Exception {
    mockMvc
        .perform(
            put("/api/v1/customers/me/location")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new CustomerLocationUpdateRequest(37.5665, 126.9780))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
  }

  @Test
  void PUT_사장_역할_403() throws Exception {
    mockMvc
        .perform(
            put("/api/v1/customers/me/location")
                .with(user(SELLER_USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new CustomerLocationUpdateRequest(37.5665, 126.9780))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
  }
}
