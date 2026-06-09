package com.magampick.support.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.magampick.support.dto.AdminInquiryAnswerRequest;
import com.magampick.support.dto.InquiryResponse;
import com.magampick.support.exception.SupportErrorCode;
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

@WebMvcTest(AdminInquiryController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class AdminInquiryControllerTest {

  @Autowired MockMvc mockMvc;
  @MockitoBean SupportService supportService;
  @MockitoBean JwtProvider jwtProvider;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private static final CustomUserDetails ADMIN = new CustomUserDetails(99L, Role.ADMIN);
  private static final CustomUserDetails CUSTOMER = new CustomUserDetails(1L, Role.CUSTOMER);

  // ── GET /api/v1/admin/inquiries ──────────────────────────────────────────────

  @Test
  void 관리자_문의_목록_200() throws Exception {
    // given
    PageResponse<InquiryResponse> pageResponse =
        new PageResponse<>(List.of(SupportFixture.aResponse()), 0, 10, 1, 1, false, false);
    given(supportService.listInquiriesForAdmin(isNull(), isNull(), any())).willReturn(pageResponse);

    mockMvc
        .perform(get("/api/v1/admin/inquiries").with(user(ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.content").isArray())
        .andExpect(jsonPath("$.data.totalCount").value(1));
  }

  @Test
  void 관리자_문의_목록_403_소비자() throws Exception {
    mockMvc
        .perform(get("/api/v1/admin/inquiries").with(user(CUSTOMER)))
        .andExpect(status().isForbidden());
  }

  @Test
  void 관리자_문의_목록_401_미인증() throws Exception {
    mockMvc.perform(get("/api/v1/admin/inquiries")).andExpect(status().isUnauthorized());
  }

  // ── POST /api/v1/admin/inquiries/{id}/answer ─────────────────────────────────

  @Test
  void 답변_200_관리자() throws Exception {
    // given
    InquiryResponse answered = SupportFixture.anAnsweredResponse();
    given(supportService.answerInquiry(eq(1L), any())).willReturn(answered);

    AdminInquiryAnswerRequest req = SupportFixture.anAnswerRequest();
    String body = objectMapper.writeValueAsString(req);

    mockMvc
        .perform(
            post("/api/v1/admin/inquiries/1/answer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(user(ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.status").value("answered"))
        .andExpect(jsonPath("$.data.answer.content").value("확인 후 처리해 드렸습니다."));
  }

  @Test
  void 답변_404_없는_문의() throws Exception {
    // given
    willThrow(new BusinessException(SupportErrorCode.INQUIRY_NOT_FOUND))
        .given(supportService)
        .answerInquiry(eq(999L), any());

    AdminInquiryAnswerRequest req = SupportFixture.anAnswerRequest();
    String body = objectMapper.writeValueAsString(req);

    mockMvc
        .perform(
            post("/api/v1/admin/inquiries/999/answer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(user(ADMIN)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("INQUIRY_NOT_FOUND"));
  }

  @Test
  void 답변_409_이미_답변된_문의() throws Exception {
    // given
    willThrow(new BusinessException(SupportErrorCode.INQUIRY_ALREADY_ANSWERED))
        .given(supportService)
        .answerInquiry(eq(1L), any());

    AdminInquiryAnswerRequest req = SupportFixture.anAnswerRequest();
    String body = objectMapper.writeValueAsString(req);

    mockMvc
        .perform(
            post("/api/v1/admin/inquiries/1/answer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(user(ADMIN)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("INQUIRY_ALREADY_ANSWERED"));
  }

  @Test
  void 답변_400_내용_빈값() throws Exception {
    // given: content blank → 400
    String body = "{\"content\":\"\"}";

    mockMvc
        .perform(
            post("/api/v1/admin/inquiries/1/answer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(user(ADMIN)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void 답변_403_소비자() throws Exception {
    AdminInquiryAnswerRequest req = SupportFixture.anAnswerRequest();
    String body = objectMapper.writeValueAsString(req);

    mockMvc
        .perform(
            post("/api/v1/admin/inquiries/1/answer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(user(CUSTOMER)))
        .andExpect(status().isForbidden());
  }
}
