package com.magampick.review.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.magampick.global.response.SliceResponse;
import com.magampick.global.security.JwtAccessDeniedHandler;
import com.magampick.global.security.JwtAuthenticationEntryPoint;
import com.magampick.global.security.JwtProvider;
import com.magampick.global.security.SecurityConfig;
import com.magampick.review.dto.StoreReviewResponse;
import com.magampick.review.fixture.ReviewFixture;
import com.magampick.review.service.ReviewQueryService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReviewController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class ReviewControllerTest {

  @Autowired MockMvc mockMvc;
  @MockitoBean ReviewQueryService reviewQueryService;
  @MockitoBean JwtProvider jwtProvider;

  private static final Long STORE_ID = 1L;

  // ── GET /api/v1/stores/{storeId}/reviews ─────────────────────────────────────

  @Test
  void GET_stores_storeId_reviews_200_목록_조회() throws Exception {
    // given
    SliceResponse<StoreReviewResponse> response =
        new SliceResponse<>(List.of(ReviewFixture.aStoreReviewResponse()), 0, 10, false);
    given(reviewQueryService.getStoreReviews(eq(STORE_ID), any())).willReturn(response);

    // when / then
    mockMvc
        .perform(get("/api/v1/stores/{storeId}/reviews", STORE_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.content").isArray())
        .andExpect(jsonPath("$.data.content[0].id").value(1))
        .andExpect(jsonPath("$.data.content[0].authorNickname").value("테스터"))
        .andExpect(jsonPath("$.data.content[0].rating").value(4))
        .andExpect(jsonPath("$.data.content[0].products[0].kind").value("deal"))
        .andExpect(jsonPath("$.data.hasNext").value(false));
  }

  @Test
  void GET_stores_storeId_reviews_200_빈_매장_빈_결과() throws Exception {
    // given
    SliceResponse<StoreReviewResponse> empty = new SliceResponse<>(List.of(), 0, 10, false);
    given(reviewQueryService.getStoreReviews(eq(STORE_ID), any())).willReturn(empty);

    // when / then
    mockMvc
        .perform(get("/api/v1/stores/{storeId}/reviews", STORE_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content").isEmpty())
        .andExpect(jsonPath("$.data.hasNext").value(false));
  }

  @Test
  void GET_stores_storeId_reviews_인증_없이_public_접근_200() throws Exception {
    // given — 인증 헤더 없이도 접근 가능 (PUBLIC_GET_PATHS: /api/v1/stores/**)
    SliceResponse<StoreReviewResponse> empty = new SliceResponse<>(List.of(), 0, 10, false);
    given(reviewQueryService.getStoreReviews(any(), any())).willReturn(empty);

    // when / then
    mockMvc
        .perform(get("/api/v1/stores/{storeId}/reviews", STORE_ID))
        // Authorization 헤더 없음
        .andExpect(status().isOk());
  }

  // ── GET /api/v1/stores/{storeId}/reviews/summary ─────────────────────────────

  @Test
  void GET_stores_storeId_reviews_summary_200_요약_조회() throws Exception {
    // given
    given(reviewQueryService.getReviewSummary(eq(STORE_ID)))
        .willReturn(ReviewFixture.aReviewSummaryResponse());

    // when / then
    mockMvc
        .perform(get("/api/v1/stores/{storeId}/reviews/summary", STORE_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.average").value(4.2))
        .andExpect(jsonPath("$.data.count").value(5))
        .andExpect(jsonPath("$.data.distribution").isArray())
        .andExpect(jsonPath("$.data.distribution.length()").value(5))
        .andExpect(jsonPath("$.data.distribution[0].star").value(5))
        .andExpect(jsonPath("$.data.distribution[4].star").value(1));
  }

  @Test
  void GET_stores_storeId_reviews_summary_200_리뷰_없는_매장() throws Exception {
    // given
    given(reviewQueryService.getReviewSummary(eq(STORE_ID)))
        .willReturn(ReviewFixture.anEmptyReviewSummaryResponse());

    // when / then
    mockMvc
        .perform(get("/api/v1/stores/{storeId}/reviews/summary", STORE_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.average").value(0.0))
        .andExpect(jsonPath("$.data.count").value(0))
        .andExpect(jsonPath("$.data.distribution.length()").value(5));
  }

  @Test
  void GET_stores_storeId_reviews_summary_인증_없이_public_접근_200() throws Exception {
    // given
    given(reviewQueryService.getReviewSummary(any()))
        .willReturn(ReviewFixture.anEmptyReviewSummaryResponse());

    // when / then
    mockMvc
        .perform(get("/api/v1/stores/{storeId}/reviews/summary", STORE_ID))
        .andExpect(status().isOk());
  }
}
