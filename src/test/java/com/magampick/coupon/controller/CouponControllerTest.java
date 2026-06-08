package com.magampick.coupon.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.magampick.coupon.domain.CouponDiscountType;
import com.magampick.coupon.domain.CouponStatus;
import com.magampick.coupon.dto.CouponEventResponse;
import com.magampick.coupon.dto.CouponResponse;
import com.magampick.coupon.exception.CouponErrorCode;
import com.magampick.coupon.fixture.CouponFixture;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CouponController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class CouponControllerTest {

  @Autowired MockMvc mockMvc;
  @MockitoBean CouponService couponService;
  @MockitoBean JwtProvider jwtProvider;

  private static final CustomUserDetails CUSTOMER_USER = new CustomUserDetails(1L, Role.CUSTOMER);
  private static final CustomUserDetails SELLER_USER = new CustomUserDetails(2L, Role.SELLER);

  // ── GET /api/v1/customers/me/coupons ─────────────────────────────────────────

  @Test
  void 쿠폰함_200_소비자() throws Exception {
    CouponResponse r = CouponFixture.aResponse(10L, CouponStatus.USABLE);
    given(couponService.getMyCoupons(eq(1L))).willReturn(List.of(r));

    mockMvc
        .perform(get("/api/v1/customers/me/coupons").with(user(CUSTOMER_USER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data[0].id").value(10))
        .andExpect(jsonPath("$.data[0].status").value("USABLE"));
  }

  @Test
  void 쿠폰함_401_미인증() throws Exception {
    mockMvc.perform(get("/api/v1/customers/me/coupons")).andExpect(status().isUnauthorized());
  }

  @Test
  void 쿠폰함_403_사장_접근_거부() throws Exception {
    mockMvc
        .perform(get("/api/v1/customers/me/coupons").with(user(SELLER_USER)))
        .andExpect(status().isForbidden());
  }

  // ── GET /api/v1/customers/me/coupons/events ───────────────────────────────────

  @Test
  void 이벤트_목록_200() throws Exception {
    CouponEventResponse r =
        new CouponEventResponse(
            2L, CouponDiscountType.AMOUNT, 3000, 10000, "봄맞이", LocalDate.now().plusDays(7), false);
    given(couponService.getEvents(eq(1L))).willReturn(List.of(r));

    mockMvc
        .perform(get("/api/v1/customers/me/coupons/events").with(user(CUSTOMER_USER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data[0].couponId").value(2))
        .andExpect(jsonPath("$.data[0].claimed").value(false));
  }

  // ── POST /api/v1/customers/me/coupons/events/{couponId}/claim ─────────────────

  @Test
  void claim_201_성공() throws Exception {
    CouponResponse r = CouponFixture.aResponse(10L, CouponStatus.USABLE);
    given(couponService.claim(eq(1L), eq(2L))).willReturn(r);

    mockMvc
        .perform(post("/api/v1/customers/me/coupons/events/2/claim").with(user(CUSTOMER_USER)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(10));
  }

  @Test
  void claim_409_이미받음() throws Exception {
    willThrow(new BusinessException(CouponErrorCode.COUPON_ALREADY_CLAIMED))
        .given(couponService)
        .claim(eq(1L), eq(2L));

    mockMvc
        .perform(post("/api/v1/customers/me/coupons/events/2/claim").with(user(CUSTOMER_USER)))
        .andExpect(status().isConflict());
  }
}
