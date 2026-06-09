package com.magampick.announcement.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.magampick.announcement.domain.NoticeTag;
import com.magampick.announcement.dto.AdminAnnouncementCreateRequest;
import com.magampick.announcement.dto.AdminAnnouncementUpdateRequest;
import com.magampick.announcement.dto.AnnouncementResponse;
import com.magampick.announcement.exception.AnnouncementErrorCode;
import com.magampick.announcement.fixture.AnnouncementFixture;
import com.magampick.announcement.service.AnnouncementService;
import com.magampick.global.exception.BusinessException;
import com.magampick.global.security.CustomUserDetails;
import com.magampick.global.security.JwtAccessDeniedHandler;
import com.magampick.global.security.JwtAuthenticationEntryPoint;
import com.magampick.global.security.JwtProvider;
import com.magampick.global.security.Role;
import com.magampick.global.security.SecurityConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminAnnouncementController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class AdminAnnouncementControllerTest {

  @Autowired MockMvc mockMvc;
  @MockitoBean AnnouncementService announcementService;
  @MockitoBean JwtProvider jwtProvider;

  private static final CustomUserDetails ADMIN = new CustomUserDetails(99L, Role.ADMIN);
  private static final CustomUserDetails CUSTOMER = new CustomUserDetails(1L, Role.CUSTOMER);

  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  // ── POST /api/v1/admin/announcements ─────────────────────────────────────────

  @Test
  void 생성_201_관리자() throws Exception {
    // given
    AnnouncementResponse response = AnnouncementFixture.aResponse();
    given(announcementService.create(any())).willReturn(response);

    AdminAnnouncementCreateRequest req = AnnouncementFixture.aCreateRequest();
    String body = objectMapper.writeValueAsString(req);

    // when / then
    mockMvc
        .perform(
            post("/api/v1/admin/announcements")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(user(ADMIN)))
        .andExpect(status().isCreated())
        .andExpect(header().exists("Location"))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(1))
        .andExpect(jsonPath("$.data.tag").value("notice"));
  }

  @Test
  void 생성_400_title_blank() throws Exception {
    // given: title 빈 문자열 → 400
    AdminAnnouncementCreateRequest req =
        new AdminAnnouncementCreateRequest(NoticeTag.NOTICE, false, "", "본문");
    String body = objectMapper.writeValueAsString(req);

    mockMvc
        .perform(
            post("/api/v1/admin/announcements")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(user(ADMIN)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
  }

  @Test
  void 생성_400_tag_null() throws Exception {
    // given: tag null → 400
    String body = "{\"tag\":null,\"pinned\":false,\"title\":\"제목\",\"body\":\"본문\"}";

    mockMvc
        .perform(
            post("/api/v1/admin/announcements")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(user(ADMIN)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void 생성_403_소비자() throws Exception {
    AdminAnnouncementCreateRequest req = AnnouncementFixture.aCreateRequest();
    String body = objectMapper.writeValueAsString(req);

    mockMvc
        .perform(
            post("/api/v1/admin/announcements")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(user(CUSTOMER)))
        .andExpect(status().isForbidden());
  }

  @Test
  void 생성_401_미인증() throws Exception {
    AdminAnnouncementCreateRequest req = AnnouncementFixture.aCreateRequest();
    String body = objectMapper.writeValueAsString(req);

    mockMvc
        .perform(
            post("/api/v1/admin/announcements")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isUnauthorized());
  }

  // ── GET /api/v1/admin/announcements ──────────────────────────────────────────

  @Test
  void 목록_200_관리자() throws Exception {
    // given
    given(announcementService.list())
        .willReturn(
            List.of(AnnouncementFixture.aPinnedResponse(), AnnouncementFixture.aResponse()));

    mockMvc
        .perform(get("/api/v1/admin/announcements").with(user(ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data.length()").value(2));
  }

  // ── PATCH /api/v1/admin/announcements/{id} ────────────────────────────────────

  @Test
  void 수정_200_관리자() throws Exception {
    // given
    AnnouncementResponse response = AnnouncementFixture.aResponse();
    given(announcementService.update(eq(1L), any())).willReturn(response);

    AdminAnnouncementUpdateRequest req =
        new AdminAnnouncementUpdateRequest(NoticeTag.EVENT, null, "새 제목", null);
    String body = objectMapper.writeValueAsString(req);

    mockMvc
        .perform(
            patch("/api/v1/admin/announcements/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(user(ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(1));
  }

  @Test
  void 수정_400_title_200자_초과() throws Exception {
    // given: title 201자 → 400
    String longTitle = "가".repeat(201);
    AdminAnnouncementUpdateRequest req =
        new AdminAnnouncementUpdateRequest(null, null, longTitle, null);
    String body = objectMapper.writeValueAsString(req);

    mockMvc
        .perform(
            patch("/api/v1/admin/announcements/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(user(ADMIN)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void 수정_404_없는_공지사항() throws Exception {
    // given
    willThrow(new BusinessException(AnnouncementErrorCode.ANNOUNCEMENT_NOT_FOUND))
        .given(announcementService)
        .update(eq(999L), any());

    AdminAnnouncementUpdateRequest req = new AdminAnnouncementUpdateRequest(null, null, null, null);
    String body = objectMapper.writeValueAsString(req);

    mockMvc
        .perform(
            patch("/api/v1/admin/announcements/999")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(user(ADMIN)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("ANNOUNCEMENT_NOT_FOUND"));
  }

  // ── DELETE /api/v1/admin/announcements/{id} ────────────────────────────────────

  @Test
  void 삭제_204_관리자() throws Exception {
    mockMvc
        .perform(delete("/api/v1/admin/announcements/1").with(user(ADMIN)))
        .andExpect(status().isNoContent());
  }

  @Test
  void 삭제_404_없는_공지사항() throws Exception {
    // given
    willThrow(new BusinessException(AnnouncementErrorCode.ANNOUNCEMENT_NOT_FOUND))
        .given(announcementService)
        .delete(999L);

    mockMvc
        .perform(delete("/api/v1/admin/announcements/999").with(user(ADMIN)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("ANNOUNCEMENT_NOT_FOUND"));
  }
}
