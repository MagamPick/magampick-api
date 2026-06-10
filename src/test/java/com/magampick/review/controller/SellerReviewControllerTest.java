package com.magampick.review.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.magampick.review.dto.ReviewReplyRequest;
import com.magampick.review.dto.StoreReviewResponse;
import com.magampick.review.exception.ReviewErrorCode;
import com.magampick.review.service.ReviewCommandService;
import com.magampick.review.service.ReviewQueryService;
import com.magampick.store.exception.StoreErrorCode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SellerReviewController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class SellerReviewControllerTest {

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @MockitoBean ReviewCommandService reviewCommandService;
  @MockitoBean ReviewQueryService reviewQueryService;
  @MockitoBean JwtProvider jwtProvider;

  private static final CustomUserDetails SELLER = new CustomUserDetails(2L, Role.SELLER);
  private static final CustomUserDetails CUSTOMER = new CustomUserDetails(1L, Role.CUSTOMER);

  // ── POST /api/v1/seller/reviews/{reviewId}/reply ──────────────────────────────

  @Test
  void 답글_작성_201() throws Exception {
    ReviewReplyRequest req = new ReviewReplyRequest("감사합니다!");
    StoreReviewResponse resp = buildStoreReviewResponse(20L);

    given(reviewCommandService.replyToReview(eq(2L), eq(20L), any())).willReturn(resp);

    mockMvc
        .perform(
            post("/api/v1/seller/reviews/20/reply")
                .with(user(SELLER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.id").value(20));
  }

  @Test
  void 답글_작성_미인증_401() throws Exception {
    ReviewReplyRequest req = new ReviewReplyRequest("감사합니다!");
    mockMvc
        .perform(
            post("/api/v1/seller/reviews/20/reply")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void 답글_작성_소비자권한_403() throws Exception {
    ReviewReplyRequest req = new ReviewReplyRequest("감사합니다!");
    mockMvc
        .perform(
            post("/api/v1/seller/reviews/20/reply")
                .with(user(CUSTOMER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isForbidden());
  }

  @Test
  void 답글_작성_내용_빈값_400() throws Exception {
    // content="" → @NotBlank 위반
    mockMvc
        .perform(
            post("/api/v1/seller/reviews/20/reply")
                .with(user(SELLER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void 답글_이미_존재_409() throws Exception {
    ReviewReplyRequest req = new ReviewReplyRequest("감사합니다!");

    willThrow(new BusinessException(ReviewErrorCode.REPLY_ALREADY_EXISTS))
        .given(reviewCommandService)
        .replyToReview(any(), any(), any());

    mockMvc
        .perform(
            post("/api/v1/seller/reviews/20/reply")
                .with(user(SELLER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("REPLY_ALREADY_EXISTS"));
  }

  @Test
  void 답글_본인매장아님_403() throws Exception {
    ReviewReplyRequest req = new ReviewReplyRequest("감사합니다!");

    willThrow(new BusinessException(ReviewErrorCode.REPLY_STORE_FORBIDDEN))
        .given(reviewCommandService)
        .replyToReview(any(), any(), any());

    mockMvc
        .perform(
            post("/api/v1/seller/reviews/20/reply")
                .with(user(SELLER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("REPLY_STORE_FORBIDDEN"));
  }

  // ── GET /api/v1/seller/stores/{storeId}/reviews ──────────────────────────────

  @Test
  void 매장_리뷰_목록_200() throws Exception {
    StoreReviewResponse resp = buildStoreReviewResponse(20L);
    given(reviewQueryService.getSellerStoreReviews(eq(2L), eq(100L))).willReturn(List.of(resp));

    mockMvc
        .perform(get("/api/v1/seller/stores/100/reviews").with(user(SELLER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].id").value(20));
  }

  @Test
  void 매장_리뷰_목록_미인증_401() throws Exception {
    mockMvc.perform(get("/api/v1/seller/stores/100/reviews")).andExpect(status().isUnauthorized());
  }

  @Test
  void 매장_리뷰_목록_소비자권한_403() throws Exception {
    mockMvc
        .perform(get("/api/v1/seller/stores/100/reviews").with(user(CUSTOMER)))
        .andExpect(status().isForbidden());
  }

  @Test
  void 매장_리뷰_목록_본인매장아님_403() throws Exception {
    willThrow(new BusinessException(StoreErrorCode.STORE_ACCESS_DENIED))
        .given(reviewQueryService)
        .getSellerStoreReviews(any(), any());

    mockMvc
        .perform(get("/api/v1/seller/stores/100/reviews").with(user(SELLER)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("STORE_ACCESS_DENIED"));
  }

  // ── 헬퍼 ─────────────────────────────────────────────────────────────────────

  private StoreReviewResponse buildStoreReviewResponse(Long id) {
    return new StoreReviewResponse(
        id,
        "테스터",
        4,
        "맛있어요",
        OffsetDateTime.now(ZoneOffset.ofHours(9)),
        List.of(),
        List.of(),
        List.of(),
        null);
  }
}
