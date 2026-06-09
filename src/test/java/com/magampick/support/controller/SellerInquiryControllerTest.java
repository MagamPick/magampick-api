package com.magampick.support.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.magampick.global.security.CustomUserDetails;
import com.magampick.global.security.JwtAccessDeniedHandler;
import com.magampick.global.security.JwtAuthenticationEntryPoint;
import com.magampick.global.security.JwtProvider;
import com.magampick.global.security.Role;
import com.magampick.global.security.SecurityConfig;
import com.magampick.support.dto.InquiryCreateRequest;
import com.magampick.support.dto.InquiryResponse;
import com.magampick.support.fixture.SupportFixture;
import com.magampick.support.service.SupportService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SellerInquiryController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class SellerInquiryControllerTest {

  @Autowired MockMvc mockMvc;
  @MockitoBean SupportService supportService;
  @MockitoBean JwtProvider jwtProvider;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private static final CustomUserDetails SELLER = new CustomUserDetails(2L, Role.SELLER);
  private static final CustomUserDetails CUSTOMER = new CustomUserDetails(1L, Role.CUSTOMER);

  // ── POST /api/v1/seller/inquiries ────────────────────────────────────────────

  @Test
  void 사장_문의_생성_201() throws Exception {
    // given
    InquiryResponse response = SupportFixture.aResponse();
    given(supportService.createInquiry(eq(Role.SELLER), eq(2L), any())).willReturn(response);

    InquiryCreateRequest req = SupportFixture.aCreateRequest();
    String body = objectMapper.writeValueAsString(req);

    mockMvc
        .perform(
            post("/api/v1/seller/inquiries")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(user(SELLER)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true));
  }

  @Test
  void 사장_문의_생성_403_소비자() throws Exception {
    InquiryCreateRequest req = SupportFixture.aCreateRequest();
    String body = objectMapper.writeValueAsString(req);

    mockMvc
        .perform(
            post("/api/v1/seller/inquiries")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(user(CUSTOMER)))
        .andExpect(status().isForbidden());
  }

  @Test
  void 사장_문의_생성_401_미인증() throws Exception {
    InquiryCreateRequest req = SupportFixture.aCreateRequest();
    String body = objectMapper.writeValueAsString(req);

    mockMvc
        .perform(
            post("/api/v1/seller/inquiries").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isUnauthorized());
  }

  // ── GET /api/v1/seller/inquiries ─────────────────────────────────────────────

  @Test
  void 사장_내_문의_목록_200() throws Exception {
    // given
    given(supportService.listMyInquiries(Role.SELLER, 2L))
        .willReturn(List.of(SupportFixture.aResponse()));

    mockMvc
        .perform(get("/api/v1/seller/inquiries").with(user(SELLER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray());
  }

  @Test
  void 사장_내_문의_목록_401_미인증() throws Exception {
    mockMvc.perform(get("/api/v1/seller/inquiries")).andExpect(status().isUnauthorized());
  }

  // ── GET /api/v1/seller/inquiries/{id} ────────────────────────────────────────

  @Test
  void 사장_문의_상세_200() throws Exception {
    // given
    given(supportService.getMyInquiry(Role.SELLER, 2L, 2L)).willReturn(SupportFixture.aResponse());

    mockMvc
        .perform(get("/api/v1/seller/inquiries/2").with(user(SELLER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));
  }
}
