package com.magampick.product.controller;

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
import com.magampick.product.dto.MenuProductDetailResponse;
import com.magampick.product.exception.ProductErrorCode;
import com.magampick.product.service.ProductDetailQueryService;
import com.magampick.store.domain.OperationStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProductQueryController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class ProductQueryControllerTest {

  @Autowired MockMvc mockMvc;
  @MockitoBean ProductDetailQueryService productDetailQueryService;
  @MockitoBean JwtProvider jwtProvider;

  private static final CustomUserDetails CUSTOMER = new CustomUserDetails(1L, Role.CUSTOMER);
  private static final CustomUserDetails SELLER = new CustomUserDetails(2L, Role.SELLER);

  private MenuProductDetailResponse aDetailResponse() {
    return new MenuProductDetailResponse(
        "menu",
        50L,
        10L,
        "테스트매장",
        0.8,
        OperationStatus.OPEN,
        "/img/bread.jpg",
        "크로아상",
        null,
        0.0,
        0L,
        "20:00",
        new BigDecimal("4500"),
        true);
  }

  // ── GET /api/v1/products/{id} ─────────────────────────────────────────────────────────────────

  @Test
  void GET_products_id_200_menu_상세() throws Exception {
    given(productDetailQueryService.getDetail(50L, 1L)).willReturn(aDetailResponse());

    mockMvc
        .perform(get("/api/v1/products/50").with(user(CUSTOMER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.kind").value("menu"))
        .andExpect(jsonPath("$.data.id").value(50))
        .andExpect(jsonPath("$.data.storeName").value("테스트매장"))
        .andExpect(jsonPath("$.data.isOnSale").value(true))
        .andExpect(jsonPath("$.data.rating").value(0.0))
        .andExpect(jsonPath("$.data.reviewCount").value(0));
  }

  @Test
  void GET_products_id_401_미인증() throws Exception {
    mockMvc.perform(get("/api/v1/products/50")).andExpect(status().isUnauthorized());
  }

  @Test
  void GET_products_id_403_사장_접근_거부() throws Exception {
    mockMvc
        .perform(get("/api/v1/products/50").with(user(SELLER)))
        .andExpect(status().isForbidden());
  }

  @Test
  void GET_products_id_404_없음() throws Exception {
    given(productDetailQueryService.getDetail(999L, 1L))
        .willThrow(new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));

    mockMvc
        .perform(get("/api/v1/products/999").with(user(CUSTOMER)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("PRODUCT_NOT_FOUND"));
  }
}
