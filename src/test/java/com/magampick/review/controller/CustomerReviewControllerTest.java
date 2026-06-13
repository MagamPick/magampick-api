package com.magampick.review.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
import com.magampick.review.dto.CreateReviewRequest;
import com.magampick.review.dto.MyReviewResponse;
import com.magampick.review.dto.ReviewableOrderResponse;
import com.magampick.review.dto.UpdateReviewRequest;
import com.magampick.review.exception.ReviewErrorCode;
import com.magampick.review.service.ReviewCommandService;
import com.magampick.review.service.ReviewQueryService;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(CustomerReviewController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class CustomerReviewControllerTest {

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @MockitoBean ReviewCommandService reviewCommandService;
  @MockitoBean ReviewQueryService reviewQueryService;
  @MockitoBean JwtProvider jwtProvider;

  private static final CustomUserDetails CUSTOMER = new CustomUserDetails(1L, Role.CUSTOMER);
  private static final CustomUserDetails SELLER = new CustomUserDetails(2L, Role.SELLER);

  /** multipart 의 JSON {@code request} 파트. */
  private MockMultipartFile requestPart(String json) {
    return new MockMultipartFile(
        "request",
        "request",
        MediaType.APPLICATION_JSON_VALUE,
        json.getBytes(StandardCharsets.UTF_8));
  }

  /** multipart() 는 기본 POST — PUT 매핑에 맞춰 메서드를 덮어쓴다. */
  private static RequestPostProcessor asPut() {
    return request -> {
      request.setMethod("PUT");
      return request;
    };
  }

  // ── GET /api/v1/orders/reviewable ─────────────────────────────────────────────

  @Test
  void 리뷰_가능한_주문_목록_200() throws Exception {
    ReviewableOrderResponse resp =
        new ReviewableOrderResponse(
            10L, 100L, "테스트 매장", List.of(), OffsetDateTime.now(ZoneOffset.ofHours(9)), false, null);
    given(reviewQueryService.getReviewableOrders(1L)).willReturn(List.of(resp));

    mockMvc
        .perform(get("/api/v1/orders/reviewable").with(user(CUSTOMER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].orderId").value(10));
  }

  @Test
  void 리뷰_가능한_주문_목록_미인증_401() throws Exception {
    mockMvc.perform(get("/api/v1/orders/reviewable")).andExpect(status().isUnauthorized());
  }

  @Test
  void 리뷰_가능한_주문_목록_사장권한_403() throws Exception {
    mockMvc
        .perform(get("/api/v1/orders/reviewable").with(user(SELLER)))
        .andExpect(status().isForbidden());
  }

  // ── GET /api/v1/orders/{orderId}/review ──────────────────────────────────────

  @Test
  void 주문별_리뷰_조회_200() throws Exception {
    MyReviewResponse resp = buildMyReviewResponse(20L);
    given(reviewQueryService.getOrderReview(1L, 10L)).willReturn(Optional.of(resp));

    mockMvc
        .perform(get("/api/v1/orders/10/review").with(user(CUSTOMER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(20));
  }

  @Test
  void 주문별_리뷰_없으면_204() throws Exception {
    given(reviewQueryService.getOrderReview(1L, 10L)).willReturn(Optional.empty());

    mockMvc
        .perform(get("/api/v1/orders/10/review").with(user(CUSTOMER)))
        .andExpect(status().isNoContent());
  }

  @Test
  void 주문별_리뷰_조회_미인증_401() throws Exception {
    mockMvc.perform(get("/api/v1/orders/10/review")).andExpect(status().isUnauthorized());
  }

  @Test
  void 주문별_리뷰_조회_사장권한_403() throws Exception {
    mockMvc
        .perform(get("/api/v1/orders/10/review").with(user(SELLER)))
        .andExpect(status().isForbidden());
  }

  // ── GET /api/v1/customers/me/reviews ─────────────────────────────────────────

  @Test
  void 소비자_본인_리뷰_목록_200() throws Exception {
    given(reviewQueryService.getMyReviews(1L)).willReturn(List.of(buildMyReviewResponse(20L)));

    mockMvc
        .perform(get("/api/v1/customers/me/reviews").with(user(CUSTOMER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].id").value(20));
  }

  @Test
  void 소비자_본인_리뷰_목록_미인증_401() throws Exception {
    mockMvc.perform(get("/api/v1/customers/me/reviews")).andExpect(status().isUnauthorized());
  }

  // ── POST /api/v1/orders/{orderId}/reviews ────────────────────────────────────

  @Test
  void 리뷰_작성_201() throws Exception {
    CreateReviewRequest req = new CreateReviewRequest(4, "맛있어요", Set.of());
    MyReviewResponse resp = buildMyReviewResponse(20L);

    given(reviewCommandService.createReview(eq(1L), eq(10L), any(), any())).willReturn(resp);

    mockMvc
        .perform(
            multipart("/api/v1/orders/10/reviews")
                .file(requestPart(objectMapper.writeValueAsString(req)))
                .with(user(CUSTOMER)))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/v1/reviews/20"))
        .andExpect(jsonPath("$.data.id").value(20));
  }

  @Test
  void 리뷰_작성_미인증_401() throws Exception {
    mockMvc
        .perform(multipart("/api/v1/orders/10/reviews").file(requestPart("{}")))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void 리뷰_작성_사장권한_403() throws Exception {
    CreateReviewRequest req = new CreateReviewRequest(4, "맛있어요", Set.of());
    mockMvc
        .perform(
            multipart("/api/v1/orders/10/reviews")
                .file(requestPart(objectMapper.writeValueAsString(req)))
                .with(user(SELLER)))
        .andExpect(status().isForbidden());
  }

  @Test
  void 리뷰_작성_별점_없음_400() throws Exception {
    // rating=null → @NotNull 위반
    mockMvc
        .perform(
            multipart("/api/v1/orders/10/reviews")
                .file(requestPart("{\"content\":\"맛있어요\"}"))
                .with(user(CUSTOMER)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void 리뷰_이미_존재_409() throws Exception {
    CreateReviewRequest req = new CreateReviewRequest(4, "맛있어요", Set.of());

    willThrow(new BusinessException(ReviewErrorCode.REVIEW_ALREADY_EXISTS))
        .given(reviewCommandService)
        .createReview(any(), any(), any(), any());

    mockMvc
        .perform(
            multipart("/api/v1/orders/10/reviews")
                .file(requestPart(objectMapper.writeValueAsString(req)))
                .with(user(CUSTOMER)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("REVIEW_ALREADY_EXISTS"));
  }

  // ── PUT /api/v1/reviews/{reviewId} ───────────────────────────────────────────

  @Test
  void 리뷰_수정_200() throws Exception {
    UpdateReviewRequest req = new UpdateReviewRequest(5, "더 맛있어요", Set.of(), List.of());
    MyReviewResponse resp = buildMyReviewResponse(20L);

    given(reviewCommandService.updateReview(eq(1L), eq(20L), any(), any())).willReturn(resp);

    mockMvc
        .perform(
            multipart("/api/v1/reviews/20")
                .file(requestPart(objectMapper.writeValueAsString(req)))
                .with(asPut())
                .with(user(CUSTOMER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(20));
  }

  @Test
  void 리뷰_수정_미인증_401() throws Exception {
    mockMvc
        .perform(multipart("/api/v1/reviews/20").file(requestPart("{}")).with(asPut()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void 리뷰_수정_사장권한_403() throws Exception {
    UpdateReviewRequest req = new UpdateReviewRequest(5, "더 맛있어요", Set.of(), List.of());
    mockMvc
        .perform(
            multipart("/api/v1/reviews/20")
                .file(requestPart(objectMapper.writeValueAsString(req)))
                .with(asPut())
                .with(user(SELLER)))
        .andExpect(status().isForbidden());
  }

  @Test
  void 리뷰_수정_답글있어서_잠김_409() throws Exception {
    UpdateReviewRequest req = new UpdateReviewRequest(5, "더 맛있어요", Set.of(), List.of());

    willThrow(new BusinessException(ReviewErrorCode.REVIEW_LOCKED))
        .given(reviewCommandService)
        .updateReview(any(), any(), any(), any());

    mockMvc
        .perform(
            multipart("/api/v1/reviews/20")
                .file(requestPart(objectMapper.writeValueAsString(req)))
                .with(asPut())
                .with(user(CUSTOMER)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("REVIEW_LOCKED"));
  }

  // ── DELETE /api/v1/reviews/{reviewId} ────────────────────────────────────────

  @Test
  void 리뷰_삭제_204() throws Exception {
    willDoNothing().given(reviewCommandService).deleteReview(1L, 20L);

    mockMvc
        .perform(delete("/api/v1/reviews/20").with(user(CUSTOMER)))
        .andExpect(status().isNoContent());
  }

  @Test
  void 리뷰_삭제_미인증_401() throws Exception {
    mockMvc.perform(delete("/api/v1/reviews/20")).andExpect(status().isUnauthorized());
  }

  @Test
  void 리뷰_삭제_사장권한_403() throws Exception {
    mockMvc
        .perform(delete("/api/v1/reviews/20").with(user(SELLER)))
        .andExpect(status().isForbidden());
  }

  @Test
  void 리뷰_삭제_본인아님_403() throws Exception {
    willThrow(new BusinessException(ReviewErrorCode.REVIEW_FORBIDDEN))
        .given(reviewCommandService)
        .deleteReview(any(), any());

    mockMvc
        .perform(delete("/api/v1/reviews/20").with(user(CUSTOMER)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("REVIEW_FORBIDDEN"));
  }

  // ── 헬퍼 ─────────────────────────────────────────────────────────────────────

  private MyReviewResponse buildMyReviewResponse(Long id) {
    return new MyReviewResponse(
        id,
        100L,
        "테스트 매장",
        List.of(),
        4,
        "맛있어요",
        List.of(),
        List.of(),
        OffsetDateTime.now(ZoneOffset.ofHours(9)),
        null);
  }
}
