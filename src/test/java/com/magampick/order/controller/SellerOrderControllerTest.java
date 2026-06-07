package com.magampick.order.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.magampick.global.exception.BusinessException;
import com.magampick.global.security.CustomUserDetails;
import com.magampick.global.security.JwtAccessDeniedHandler;
import com.magampick.global.security.JwtAuthenticationEntryPoint;
import com.magampick.global.security.JwtProvider;
import com.magampick.global.security.Role;
import com.magampick.global.security.SecurityConfig;
import com.magampick.order.exception.OrderErrorCode;
import com.magampick.order.fixture.OrderFixture;
import com.magampick.order.service.OrderService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SellerOrderController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class SellerOrderControllerTest {

  @Autowired MockMvc mockMvc;
  @MockitoBean OrderService orderService;
  @MockitoBean JwtProvider jwtProvider;

  private static final CustomUserDetails CUSTOMER = new CustomUserDetails(1L, Role.CUSTOMER);
  private static final CustomUserDetails SELLER = new CustomUserDetails(2L, Role.SELLER);

  // ── GET /api/v1/seller/stores/{storeId}/orders — 사장 목록 ──────────────────

  @Test
  void 사장_매장_주문_목록_200() throws Exception {
    // given
    given(orderService.listStoreOrders(eq(2L), eq(10L), anyString()))
        .willReturn(List.of(OrderFixture.aSellerOrderResponse(42L)));

    // when / then
    mockMvc
        .perform(get("/api/v1/seller/stores/10/orders").with(user(SELLER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data[0].id").value(42));
  }

  @Test
  void 사장_매장_주문_목록_미인증_401() throws Exception {
    mockMvc.perform(get("/api/v1/seller/stores/10/orders")).andExpect(status().isUnauthorized());
  }

  @Test
  void 사장_매장_주문_목록_소비자권한_403() throws Exception {
    mockMvc
        .perform(get("/api/v1/seller/stores/10/orders").with(user(CUSTOMER)))
        .andExpect(status().isForbidden());
  }

  // ── GET /api/v1/seller/orders/{id} — 사장 상세 ──────────────────────────────

  @Test
  void 사장_주문_상세_200() throws Exception {
    // given
    given(orderService.getStoreOrder(2L, 42L)).willReturn(OrderFixture.aSellerOrderResponse(42L));

    // when / then
    mockMvc
        .perform(get("/api/v1/seller/orders/42").with(user(SELLER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(42))
        .andExpect(jsonPath("$.data.customerName").isString());
  }

  @Test
  void 사장_주문_상세_미인증_401() throws Exception {
    mockMvc.perform(get("/api/v1/seller/orders/42")).andExpect(status().isUnauthorized());
  }

  @Test
  void 사장_주문_상세_소비자권한_403() throws Exception {
    mockMvc
        .perform(get("/api/v1/seller/orders/42").with(user(CUSTOMER)))
        .andExpect(status().isForbidden());
  }

  @Test
  void 사장_주문_없음_404() throws Exception {
    // given
    given(orderService.getStoreOrder(2L, 99L))
        .willThrow(new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));

    // when / then
    mockMvc
        .perform(get("/api/v1/seller/orders/99").with(user(SELLER)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("ORDER_NOT_FOUND"));
  }
}
