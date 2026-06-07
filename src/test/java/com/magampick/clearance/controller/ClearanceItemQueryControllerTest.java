package com.magampick.clearance.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.magampick.address.exception.AddressErrorCode;
import com.magampick.clearance.dto.ClosingDealResponse;
import com.magampick.clearance.dto.DealProductDetailResponse;
import com.magampick.clearance.exception.ClearanceItemErrorCode;
import com.magampick.clearance.service.ClearanceItemDetailQueryService;
import com.magampick.clearance.service.ClosingDealQueryService;
import com.magampick.global.exception.BusinessException;
import com.magampick.global.security.CustomUserDetails;
import com.magampick.global.security.JwtAccessDeniedHandler;
import com.magampick.global.security.JwtAuthenticationEntryPoint;
import com.magampick.global.security.JwtProvider;
import com.magampick.global.security.Role;
import com.magampick.global.security.SecurityConfig;
import com.magampick.store.domain.OperationStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ClearanceItemQueryController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class ClearanceItemQueryControllerTest {

  @Autowired MockMvc mockMvc;
  @MockitoBean ClearanceItemDetailQueryService clearanceItemDetailQueryService;
  @MockitoBean ClosingDealQueryService closingDealQueryService;
  @MockitoBean JwtProvider jwtProvider;

  private static final CustomUserDetails CUSTOMER = new CustomUserDetails(1L, Role.CUSTOMER);
  private static final CustomUserDetails SELLER = new CustomUserDetails(2L, Role.SELLER);

  private DealProductDetailResponse aDetailResponse() {
    return new DealProductDetailResponse(
        "deal",
        100L,
        10L,
        "테스트매장",
        1.2,
        OperationStatus.OPEN,
        "/img/bread.jpg",
        "크로아상",
        null,
        4.0,
        5L,
        "21:00",
        new BigDecimal("4500"),
        new BigDecimal("3000"),
        33,
        LocalDateTime.now().plusHours(2),
        3,
        "ACTIVE");
  }

  // ── GET /api/v1/clearance-items/{id} ──────────────────────────────────────────────────────────

  @Test
  void GET_clearance_items_id_200_deal_상세() throws Exception {
    given(clearanceItemDetailQueryService.getDetail(100L, 1L)).willReturn(aDetailResponse());

    mockMvc
        .perform(get("/api/v1/clearance-items/100").with(user(CUSTOMER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.kind").value("deal"))
        .andExpect(jsonPath("$.data.id").value(100))
        .andExpect(jsonPath("$.data.storeName").value("테스트매장"))
        .andExpect(jsonPath("$.data.dealStatus").value("ACTIVE"));
  }

  @Test
  void GET_clearance_items_id_401_미인증() throws Exception {
    mockMvc.perform(get("/api/v1/clearance-items/100")).andExpect(status().isUnauthorized());
  }

  @Test
  void GET_clearance_items_id_403_사장_접근_거부() throws Exception {
    mockMvc
        .perform(get("/api/v1/clearance-items/100").with(user(SELLER)))
        .andExpect(status().isForbidden());
  }

  @Test
  void GET_clearance_items_id_404_없음() throws Exception {
    given(clearanceItemDetailQueryService.getDetail(999L, 1L))
        .willThrow(new BusinessException(ClearanceItemErrorCode.CLEARANCE_ITEM_NOT_FOUND));

    mockMvc
        .perform(get("/api/v1/clearance-items/999").with(user(CUSTOMER)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("CLEARANCE_ITEM_NOT_FOUND"));
  }

  // ── GET /api/v1/clearance-items/closing-soon ──────────────────────────────────────────────────

  @Test
  void GET_closing_soon_200_정상() throws Exception {
    ClosingDealResponse item =
        new ClosingDealResponse(
            1L,
            "우리빵집",
            "크로아상",
            "/img/croissant.jpg",
            33,
            new BigDecimal("4500"),
            new BigDecimal("3000"),
            LocalDateTime.now().plusMinutes(45));
    given(closingDealQueryService.getClosingSoonDeals(1L)).willReturn(List.of(item));

    mockMvc
        .perform(get("/api/v1/clearance-items/closing-soon").with(user(CUSTOMER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data[0].id").value(1))
        .andExpect(jsonPath("$.data[0].storeName").value("우리빵집"))
        .andExpect(jsonPath("$.data[0].discountRate").value(33));
  }

  @Test
  void GET_closing_soon_401_미인증() throws Exception {
    mockMvc
        .perform(get("/api/v1/clearance-items/closing-soon"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void GET_closing_soon_403_사장_접근_거부() throws Exception {
    mockMvc
        .perform(get("/api/v1/clearance-items/closing-soon").with(user(SELLER)))
        .andExpect(status().isForbidden());
  }

  @Test
  void GET_closing_soon_400_기본주소지_없음() throws Exception {
    given(closingDealQueryService.getClosingSoonDeals(1L))
        .willThrow(new BusinessException(AddressErrorCode.DEFAULT_ADDRESS_REQUIRED));

    mockMvc
        .perform(get("/api/v1/clearance-items/closing-soon").with(user(CUSTOMER)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("DEFAULT_ADDRESS_REQUIRED"));
  }
}
