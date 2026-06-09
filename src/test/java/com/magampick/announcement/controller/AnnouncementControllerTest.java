package com.magampick.announcement.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.magampick.announcement.dto.AnnouncementResponse;
import com.magampick.announcement.fixture.AnnouncementFixture;
import com.magampick.announcement.service.AnnouncementService;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AnnouncementController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class AnnouncementControllerTest {

  @Autowired MockMvc mockMvc;
  @MockitoBean AnnouncementService announcementService;
  @MockitoBean JwtProvider jwtProvider;

  private static final CustomUserDetails CUSTOMER = new CustomUserDetails(1L, Role.CUSTOMER);
  private static final CustomUserDetails SELLER = new CustomUserDetails(2L, Role.SELLER);
  private static final CustomUserDetails ADMIN = new CustomUserDetails(99L, Role.ADMIN);

  // ── GET /api/v1/announcements ─────────────────────────────────────────────────

  @Test
  void 목록_200_소비자() throws Exception {
    // given
    AnnouncementResponse pinned = AnnouncementFixture.aPinnedResponse();
    AnnouncementResponse normal = AnnouncementFixture.aResponse();
    given(announcementService.list()).willReturn(List.of(pinned, normal));

    // when / then
    mockMvc
        .perform(get("/api/v1/announcements").with(user(CUSTOMER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data[0].id").value(2))
        .andExpect(jsonPath("$.data[0].pinned").value(true))
        // NoticeTag @JsonValue → 소문자 직렬화
        .andExpect(jsonPath("$.data[0].tag").value("update"))
        // date 필드명 확인 (publishedAt 아님)
        .andExpect(jsonPath("$.data[0].date").value("2026-05-26"))
        .andExpect(jsonPath("$.data[1].tag").value("notice"));
  }

  @Test
  void 목록_200_사장도_접근_가능() throws Exception {
    // given
    given(announcementService.list()).willReturn(List.of());

    // when / then
    mockMvc.perform(get("/api/v1/announcements").with(user(SELLER))).andExpect(status().isOk());
  }

  @Test
  void 목록_200_관리자도_접근_가능() throws Exception {
    // given
    given(announcementService.list()).willReturn(List.of());

    // when / then
    mockMvc.perform(get("/api/v1/announcements").with(user(ADMIN))).andExpect(status().isOk());
  }

  @Test
  void 목록_401_미인증() throws Exception {
    // when / then: 인증 없이 요청 → 401
    mockMvc.perform(get("/api/v1/announcements")).andExpect(status().isUnauthorized());
  }
}
