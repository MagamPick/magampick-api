package com.magampick.support.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.magampick.global.exception.BusinessException;
import com.magampick.global.security.CustomUserDetails;
import com.magampick.global.security.JwtAccessDeniedHandler;
import com.magampick.global.security.JwtAuthenticationEntryPoint;
import com.magampick.global.security.JwtProvider;
import com.magampick.global.security.Role;
import com.magampick.global.security.SecurityConfig;
import com.magampick.support.dto.InquiryCreateRequest;
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

@WebMvcTest(CustomerInquiryController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class CustomerInquiryControllerTest {

  @Autowired MockMvc mockMvc;
  @MockitoBean SupportService supportService;
  @MockitoBean JwtProvider jwtProvider;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private static final CustomUserDetails CUSTOMER = new CustomUserDetails(1L, Role.CUSTOMER);
  private static final CustomUserDetails SELLER = new CustomUserDetails(2L, Role.SELLER);

  // ── POST /api/v1/customers/me/inquiries ──────────────────────────────────────

  @Test
  void 문의_생성_201_소비자() throws Exception {
    // given
    InquiryResponse response = SupportFixture.aResponse();
    given(supportService.createInquiry(eq(Role.CUSTOMER), eq(1L), any())).willReturn(response);

    InquiryCreateRequest req = SupportFixture.aCreateRequest();
    String body = objectMapper.writeValueAsString(req);

    // when / then
    mockMvc
        .perform(
            post("/api/v1/customers/me/inquiries")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(user(CUSTOMER)))
        .andExpect(status().isCreated())
        .andExpect(header().exists("Location"))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(1))
        // InquiryCategory @JsonValue → 소문자 직렬화
        .andExpect(jsonPath("$.data.category").value("payment"))
        // InquiryStatus @JsonValue → 소문자 직렬화
        .andExpect(jsonPath("$.data.status").value("pending"))
        // createdAt date 형식
        .andExpect(jsonPath("$.data.createdAt").value("2026-06-09"))
        // 답변 없음
        .andExpect(jsonPath("$.data.answer").doesNotExist());
  }

  @Test
  void 문의_생성_400_title_1자() throws Exception {
    // given: title 1자 → @Size(min=2) 위반
    String body = "{\"category\":\"payment\",\"title\":\"짧\",\"content\":\"열자이상의내용이있어야합니다\"}";

    mockMvc
        .perform(
            post("/api/v1/customers/me/inquiries")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(user(CUSTOMER)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
  }

  @Test
  void 문의_생성_400_content_9자() throws Exception {
    // given: content 9자 → @Size(min=10) 위반
    String body = "{\"category\":\"payment\",\"title\":\"정상제목\",\"content\":\"9자짧은글\"}";

    mockMvc
        .perform(
            post("/api/v1/customers/me/inquiries")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(user(CUSTOMER)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void 문의_생성_400_category_소문자_역직렬화_성공() throws Exception {
    // given: "payment" (소문자) → InquiryCategory.PAYMENT 역직렬화 성공 → 정상 처리
    InquiryResponse response = SupportFixture.aResponse();
    given(supportService.createInquiry(eq(Role.CUSTOMER), eq(1L), any())).willReturn(response);

    String body =
        "{\"category\":\"payment\",\"title\":\"정상제목입니다\",\"content\":\"열자이상의내용이있어야합니다요\"}";

    mockMvc
        .perform(
            post("/api/v1/customers/me/inquiries")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(user(CUSTOMER)))
        .andExpect(status().isCreated());
  }

  @Test
  void 문의_생성_403_사장() throws Exception {
    InquiryCreateRequest req = SupportFixture.aCreateRequest();
    String body = objectMapper.writeValueAsString(req);

    mockMvc
        .perform(
            post("/api/v1/customers/me/inquiries")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(user(SELLER)))
        .andExpect(status().isForbidden());
  }

  @Test
  void 문의_생성_401_미인증() throws Exception {
    InquiryCreateRequest req = SupportFixture.aCreateRequest();
    String body = objectMapper.writeValueAsString(req);

    mockMvc
        .perform(
            post("/api/v1/customers/me/inquiries")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isUnauthorized());
  }

  // ── GET /api/v1/customers/me/inquiries ───────────────────────────────────────

  @Test
  void 내_문의_목록_200_소비자() throws Exception {
    // given
    given(supportService.listMyInquiries(Role.CUSTOMER, 1L))
        .willReturn(List.of(SupportFixture.aResponse()));

    mockMvc
        .perform(get("/api/v1/customers/me/inquiries").with(user(CUSTOMER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data.length()").value(1));
  }

  @Test
  void 내_문의_목록_401_미인증() throws Exception {
    mockMvc.perform(get("/api/v1/customers/me/inquiries")).andExpect(status().isUnauthorized());
  }

  // ── GET /api/v1/customers/me/inquiries/{id} ──────────────────────────────────

  @Test
  void 내_문의_상세_200_소비자() throws Exception {
    // given
    InquiryResponse response = SupportFixture.aResponse();
    given(supportService.getMyInquiry(Role.CUSTOMER, 1L, 1L)).willReturn(response);

    mockMvc
        .perform(get("/api/v1/customers/me/inquiries/1").with(user(CUSTOMER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(1));
  }

  @Test
  void 내_문의_상세_404_타인_것() throws Exception {
    // given: 타인 문의 접근 → 404
    willThrow(new BusinessException(SupportErrorCode.INQUIRY_NOT_FOUND))
        .given(supportService)
        .getMyInquiry(Role.CUSTOMER, 1L, 999L);

    mockMvc
        .perform(get("/api/v1/customers/me/inquiries/999").with(user(CUSTOMER)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("INQUIRY_NOT_FOUND"));
  }

  /** InquiryCategory 역직렬화 테스트 — 소문자 "order" → ORDER. */
  @Test
  void 카테고리_소문자_요청_역직렬화_성공() throws Exception {
    // given
    InquiryResponse response = SupportFixture.aResponse();
    given(supportService.createInquiry(any(), any(), any())).willReturn(response);

    String body =
        "{\"category\":\"order\",\"title\":\"주문 관련 문의\",\"content\":\"주문이 안 되는 것 같아요 확인해 주세요\"}";

    mockMvc
        .perform(
            post("/api/v1/customers/me/inquiries")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(user(CUSTOMER)))
        .andExpect(status().isCreated());
  }
}
