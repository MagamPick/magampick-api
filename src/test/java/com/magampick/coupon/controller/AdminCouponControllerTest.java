package com.magampick.coupon.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.magampick.coupon.domain.CouponDiscountType;
import com.magampick.coupon.dto.AdminCouponCreateRequest;
import com.magampick.coupon.dto.AdminCouponResponse;
import com.magampick.coupon.service.CouponService;
import com.magampick.global.security.CustomUserDetails;
import com.magampick.global.security.JwtAccessDeniedHandler;
import com.magampick.global.security.JwtAuthenticationEntryPoint;
import com.magampick.global.security.JwtProvider;
import com.magampick.global.security.Role;
import com.magampick.global.security.SecurityConfig;
import java.time.LocalDate;
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

  private String validBody() throws Exception {
    AdminCouponCreateRequest req =
        new AdminCouponCreateRequest(
            "이벤트 쿠폰", CouponDiscountType.AMOUNT, 3000, 10000, LocalDate.now().plusDays(7), 100);
    return objectMapper.writeValueAsString(req);
  }

  // ── POST /api/v1/admin/coupons ────────────────────────────────────────────────

  @Test
  void 생성_201_관리자() throws Exception {
    AdminCouponResponse response =
        new AdminCouponResponse(
            1L,
            "이벤트 쿠폰",
            CouponDiscountType.AMOUNT,
            3000,
            10000,
            LocalDate.now().plusDays(7),
            100,
            0,
            true);
    given(couponService.createEvent(any())).willReturn(response);

    mockMvc
        .perform(
            post("/api/v1/admin/coupons")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody())
                .with(user(ADMIN)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(1))
        .andExpect(jsonPath("$.data.label").value("이벤트 쿠폰"));
  }

  @Test
  void 생성_403_사장() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/coupons")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody())
                .with(user(SELLER)))
        .andExpect(status().isForbidden());
  }

  @Test
  void 생성_401_미인증() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/coupons")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void 생성_400_검증실패_label_blank() throws Exception {
    AdminCouponCreateRequest req =
        new AdminCouponCreateRequest(
            "", CouponDiscountType.AMOUNT, 3000, 10000, LocalDate.now().plusDays(7), 100);
    String body = objectMapper.writeValueAsString(req);

    mockMvc
        .perform(
            post("/api/v1/admin/coupons")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(user(ADMIN)))
        .andExpect(status().isBadRequest());
  }
}
