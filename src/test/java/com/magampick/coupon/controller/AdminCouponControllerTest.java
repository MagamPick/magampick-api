package com.magampick.coupon.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.magampick.coupon.domain.CouponDiscountType;
import com.magampick.coupon.domain.EventStatus;
import com.magampick.coupon.dto.AdminCouponCreateRequest;
import com.magampick.coupon.dto.AdminCouponResponse;
import com.magampick.coupon.dto.AdminCouponUpdateRequest;
import com.magampick.coupon.exception.CouponErrorCode;
import com.magampick.coupon.service.CouponService;
import com.magampick.global.exception.BusinessException;
import com.magampick.global.security.CustomUserDetails;
import com.magampick.global.security.JwtAccessDeniedHandler;
import com.magampick.global.security.JwtAuthenticationEntryPoint;
import com.magampick.global.security.JwtProvider;
import com.magampick.global.security.Role;
import com.magampick.global.security.SecurityConfig;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminCouponController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class AdminCouponControllerTest {

  @Autowired MockMvc mockMvc;
  @MockitoBean CouponService couponService;
  @MockitoBean JwtProvider jwtProvider;

  private static final CustomUserDetails ADMIN = new CustomUserDetails(99L, Role.ADMIN);
  private static final CustomUserDetails SELLER = new CustomUserDetails(2L, Role.SELLER);

  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  private AdminCouponResponse sampleAdminResponse() {
    return new AdminCouponResponse(
        1L,
        "이벤트 쿠폰",
        CouponDiscountType.AMOUNT,
        3000,
        10000,
        LocalDate.of(2026, 12, 31),
        100,
        0,
        true,
        LocalDate.of(2026, 6, 9),
        LocalDate.of(2026, 12, 31),
        EventStatus.ONGOING);
  }

  private String validCreateBody() throws Exception {
    AdminCouponCreateRequest req =
        new AdminCouponCreateRequest(
            "이벤트 쿠폰",
            CouponDiscountType.AMOUNT,
            3000,
            10000,
            LocalDate.of(2026, 12, 31),
            100,
            LocalDate.of(2026, 6, 9),
            LocalDate.of(2026, 12, 31));
    return objectMapper.writeValueAsString(req);
  }

  // ── POST /api/v1/admin/coupons ────────────────────────────────────────────────

  @Test
  void 생성_201_관리자() throws Exception {
    given(couponService.createEvent(any())).willReturn(sampleAdminResponse());

    mockMvc
        .perform(
            post("/api/v1/admin/coupons")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validCreateBody())
                .with(user(ADMIN)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(1))
        .andExpect(jsonPath("$.data.label").value("이벤트 쿠폰"))
        .andExpect(jsonPath("$.data.status").value("ongoing"))
        .andExpect(jsonPath("$.data.displayStartAt").value("2026-06-09"));
  }

  @Test
  void 생성_403_사장() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/coupons")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validCreateBody())
                .with(user(SELLER)))
        .andExpect(status().isForbidden());
  }

  @Test
  void 생성_401_미인증() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/coupons")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validCreateBody()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void 생성_400_검증실패_label_blank() throws Exception {
    AdminCouponCreateRequest req =
        new AdminCouponCreateRequest(
            "",
            CouponDiscountType.AMOUNT,
            3000,
            10000,
            LocalDate.of(2026, 12, 31),
            100,
            LocalDate.of(2026, 6, 9),
            LocalDate.of(2026, 12, 31));
    String body = objectMapper.writeValueAsString(req);

    mockMvc
        .perform(
            post("/api/v1/admin/coupons")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(user(ADMIN)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void 생성_400_displayStartAt_누락() throws Exception {
    // displayStartAt = null → @NotNull 검증 실패
    String body =
        """
        {"label":"이벤트","discountType":"AMOUNT","value":1000,"minOrder":0,
         "validUntil":"2026-12-31","issueLimit":null,
         "displayStartAt":null,"displayEndAt":"2026-12-31"}
        """;

    mockMvc
        .perform(
            post("/api/v1/admin/coupons")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(user(ADMIN)))
        .andExpect(status().isBadRequest());
  }

  // ── GET /api/v1/admin/coupons ─────────────────────────────────────────────────

  @Test
  void 목록조회_200_관리자() throws Exception {
    given(couponService.listEvents()).willReturn(List.of(sampleAdminResponse()));

    mockMvc
        .perform(get("/api/v1/admin/coupons").with(user(ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data[0].id").value(1))
        .andExpect(jsonPath("$.data[0].status").value("ongoing"));
  }

  @Test
  void 목록조회_403_사장() throws Exception {
    mockMvc
        .perform(get("/api/v1/admin/coupons").with(user(SELLER)))
        .andExpect(status().isForbidden());
  }

  // ── PATCH /api/v1/admin/coupons/{couponId} ────────────────────────────────────

  @Test
  void 수정_200_관리자() throws Exception {
    AdminCouponUpdateRequest req =
        new AdminCouponUpdateRequest("수정된 이름", null, null, null, null, null, null, null);
    String body = objectMapper.writeValueAsString(req);

    given(couponService.updateEvent(eq(1L), any())).willReturn(sampleAdminResponse());

    mockMvc
        .perform(
            patch("/api/v1/admin/coupons/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(user(ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(1));
  }

  @Test
  void 수정_404_없는쿠폰() throws Exception {
    AdminCouponUpdateRequest req =
        new AdminCouponUpdateRequest("수정", null, null, null, null, null, null, null);
    String body = objectMapper.writeValueAsString(req);

    given(couponService.updateEvent(eq(999L), any()))
        .willThrow(new BusinessException(CouponErrorCode.COUPON_NOT_FOUND));

    mockMvc
        .perform(
            patch("/api/v1/admin/coupons/999")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(user(ADMIN)))
        .andExpect(status().isNotFound());
  }

  // ── POST /api/v1/admin/coupons/{couponId}/end ─────────────────────────────────

  @Test
  void 조기종료_200_관리자() throws Exception {
    AdminCouponResponse endedResponse =
        new AdminCouponResponse(
            1L,
            "이벤트 쿠폰",
            CouponDiscountType.AMOUNT,
            3000,
            10000,
            LocalDate.of(2026, 12, 31),
            100,
            0,
            false, // active=false
            LocalDate.of(2026, 6, 9),
            LocalDate.of(2026, 12, 31),
            EventStatus.ENDED);

    given(couponService.endEvent(1L)).willReturn(endedResponse);

    mockMvc
        .perform(post("/api/v1/admin/coupons/1/end").with(user(ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.active").value(false))
        .andExpect(jsonPath("$.data.status").value("ended"));
  }

  @Test
  void 조기종료_404_없는쿠폰() throws Exception {
    given(couponService.endEvent(999L))
        .willThrow(new BusinessException(CouponErrorCode.COUPON_NOT_FOUND));

    mockMvc
        .perform(post("/api/v1/admin/coupons/999/end").with(user(ADMIN)))
        .andExpect(status().isNotFound());
  }
}
