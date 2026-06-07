package com.magampick.order.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.magampick.clearance.exception.ClearanceItemErrorCode;
import com.magampick.global.exception.BusinessException;
import com.magampick.global.security.CustomUserDetails;
import com.magampick.global.security.JwtAccessDeniedHandler;
import com.magampick.global.security.JwtAuthenticationEntryPoint;
import com.magampick.global.security.JwtProvider;
import com.magampick.global.security.Role;
import com.magampick.global.security.SecurityConfig;
import com.magampick.order.domain.ItemKind;
import com.magampick.order.domain.PickupType;
import com.magampick.order.dto.CreateOrderRequest;
import com.magampick.order.dto.CreateOrderRequest.OrderItemRequest;
import com.magampick.order.dto.CreateOrderRequest.PickupRequest;
import com.magampick.order.exception.OrderErrorCode;
import com.magampick.order.fixture.OrderFixture;
import com.magampick.order.service.OrderService;
import com.magampick.store.exception.StoreErrorCode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OrderController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class OrderControllerTest {

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @MockitoBean OrderService orderService;
  @MockitoBean JwtProvider jwtProvider;

  private static final CustomUserDetails CUSTOMER = new CustomUserDetails(1L, Role.CUSTOMER);
  private static final CustomUserDetails SELLER = new CustomUserDetails(2L, Role.SELLER);

  private String validRequestJson() throws Exception {
    CreateOrderRequest req =
        new CreateOrderRequest(
            10L,
            List.of(new OrderItemRequest(ItemKind.DEAL, 100L, 2)),
            new PickupRequest(PickupType.ASAP, null),
            null,
            "toss",
            true,
            null);
    return objectMapper.writeValueAsString(req);
  }

  // ── 성공 ─────────────────────────────────────────────────────────────────────

  @Test
  void 주문생성_201() throws Exception {
    // given
    given(orderService.createOrder(eq(1L), any()))
        .willReturn(OrderFixture.aPrepareOrderResponse(42L));

    // when / then
    mockMvc
        .perform(
            post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequestJson())
                .with(user(CUSTOMER)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.orderId").value(42))
        .andExpect(jsonPath("$.data.tossOrderId").value("order-42"))
        .andExpect(jsonPath("$.data.amount").isNumber());
  }

  // ── 인증·인가 ─────────────────────────────────────────────────────────────────

  @Test
  void 미인증_401() throws Exception {
    // when / then — 인증 없이 요청
    mockMvc
        .perform(
            post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequestJson()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void 사장권한_403() throws Exception {
    // when / then — SELLER 로는 주문 생성 불가
    mockMvc
        .perform(
            post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequestJson())
                .with(user(SELLER)))
        .andExpect(status().isForbidden());
  }

  // ── 입력 검증 실패 ────────────────────────────────────────────────────────────

  @Test
  void 빈_요청_400() throws Exception {
    // given — storeId 누락
    mockMvc
        .perform(
            post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(user(CUSTOMER)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
  }

  @Test
  void 결제동의_false_400() throws Exception {
    // given
    willThrow(new BusinessException(OrderErrorCode.PAYMENT_NOT_AGREED))
        .given(orderService)
        .createOrder(any(), any());

    mockMvc
        .perform(
            post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequestJson())
                .with(user(CUSTOMER)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("PAYMENT_NOT_AGREED"));
  }

  // ── 비즈니스 오류 ─────────────────────────────────────────────────────────────

  @Test
  void 매장_영업중아님_409() throws Exception {
    // given
    willThrow(new BusinessException(StoreErrorCode.STORE_CLOSED))
        .given(orderService)
        .createOrder(any(), any());

    mockMvc
        .perform(
            post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequestJson())
                .with(user(CUSTOMER)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("STORE_CLOSED"));
  }

  @Test
  void 재고부족_409() throws Exception {
    // given
    willThrow(new BusinessException(ClearanceItemErrorCode.OUT_OF_STOCK))
        .given(orderService)
        .createOrder(any(), any());

    mockMvc
        .perform(
            post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequestJson())
                .with(user(CUSTOMER)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("OUT_OF_STOCK"));
  }

  @Test
  void 금액불일치_400() throws Exception {
    // given
    willThrow(new BusinessException(OrderErrorCode.AMOUNT_MISMATCH))
        .given(orderService)
        .createOrder(any(), any());

    mockMvc
        .perform(
            post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequestJson())
                .with(user(CUSTOMER)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("AMOUNT_MISMATCH"));
  }

  @Test
  void 결제수단_미지원_400() throws Exception {
    // given — paymentMethod="kakao" → @Pattern(regexp="toss") 위반
    CreateOrderRequest req =
        new CreateOrderRequest(
            10L,
            List.of(new OrderItemRequest(ItemKind.DEAL, 100L, 2)),
            new PickupRequest(PickupType.ASAP, null),
            null,
            "kakao",
            true,
            null);

    mockMvc
        .perform(
            post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
                .with(user(CUSTOMER)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
  }

  @Test
  void 수량_초과_400() throws Exception {
    // given — quantity=11 → @Max(10) 위반
    CreateOrderRequest req =
        new CreateOrderRequest(
            10L,
            List.of(new OrderItemRequest(ItemKind.DEAL, 100L, 11)),
            new PickupRequest(PickupType.ASAP, null),
            null,
            "toss",
            true,
            null);

    mockMvc
        .perform(
            post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
                .with(user(CUSTOMER)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
  }

  @Test
  void 메모_길이초과_400() throws Exception {
    // given — memo 81자 → @Size(max=80) 위반
    String longMemo = "a".repeat(81);
    CreateOrderRequest req =
        new CreateOrderRequest(
            10L,
            List.of(new OrderItemRequest(ItemKind.DEAL, 100L, 2)),
            new PickupRequest(PickupType.ASAP, null),
            longMemo,
            "toss",
            true,
            null);

    mockMvc
        .perform(
            post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
                .with(user(CUSTOMER)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
  }

  // ── GET /api/v1/orders — 소비자 목록 ────────────────────────────────────────

  @Test
  void 소비자_주문_목록_200() throws Exception {
    // given
    given(orderService.listMyOrders(eq(1L), anyString()))
        .willReturn(List.of(OrderFixture.anOrderResponse(42L)));

    // when / then
    mockMvc
        .perform(get("/api/v1/orders").with(user(CUSTOMER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data[0].id").value(42));
  }

  @Test
  void 소비자_주문_목록_미인증_401() throws Exception {
    mockMvc.perform(get("/api/v1/orders")).andExpect(status().isUnauthorized());
  }

  @Test
  void 소비자_주문_목록_사장권한_403() throws Exception {
    mockMvc.perform(get("/api/v1/orders").with(user(SELLER))).andExpect(status().isForbidden());
  }

  // ── GET /api/v1/orders/{id} — 소비자 상세 ────────────────────────────────────

  @Test
  void 소비자_주문_상세_200() throws Exception {
    // given
    given(orderService.getMyOrder(1L, 42L)).willReturn(OrderFixture.anOrderResponse(42L));

    // when / then
    mockMvc
        .perform(get("/api/v1/orders/42").with(user(CUSTOMER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(42));
  }

  @Test
  void 소비자_주문_상세_미인증_401() throws Exception {
    mockMvc.perform(get("/api/v1/orders/42")).andExpect(status().isUnauthorized());
  }

  @Test
  void 소비자_주문_상세_사장권한_403() throws Exception {
    mockMvc.perform(get("/api/v1/orders/42").with(user(SELLER))).andExpect(status().isForbidden());
  }

  @Test
  void 소비자_주문_없음_404() throws Exception {
    // given
    given(orderService.getMyOrder(1L, 99L))
        .willThrow(new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));

    // when / then
    mockMvc
        .perform(get("/api/v1/orders/99").with(user(CUSTOMER)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("ORDER_NOT_FOUND"));
  }

  @Test
  void 타인_주문_조회시_403_응답() throws Exception {
    // given
    given(orderService.getMyOrder(1L, 42L))
        .willThrow(new BusinessException(OrderErrorCode.ORDER_FORBIDDEN));

    // when / then
    mockMvc
        .perform(get("/api/v1/orders/42").with(user(CUSTOMER)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("ORDER_FORBIDDEN"));
  }

  // ── POST /api/v1/orders/{id}/cancel — 소비자 취소 ────────────────────────────

  @Test
  void 소비자_주문_취소_200() throws Exception {
    // given
    given(orderService.cancelOrder(1L, 42L)).willReturn(OrderFixture.anOrderResponse(42L));

    // when / then
    mockMvc
        .perform(post("/api/v1/orders/42/cancel").with(user(CUSTOMER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(42));
  }

  @Test
  void 취소_미인증_401() throws Exception {
    mockMvc.perform(post("/api/v1/orders/42/cancel")).andExpect(status().isUnauthorized());
  }

  @Test
  void 취소_사장권한_403() throws Exception {
    mockMvc
        .perform(post("/api/v1/orders/42/cancel").with(user(SELLER)))
        .andExpect(status().isForbidden());
  }

  @Test
  void 취소_잘못된전이_409() throws Exception {
    // given — PENDING 아닌 상태에서 취소 시도
    willThrow(new BusinessException(OrderErrorCode.INVALID_ORDER_TRANSITION))
        .given(orderService)
        .cancelOrder(1L, 42L);

    mockMvc
        .perform(post("/api/v1/orders/42/cancel").with(user(CUSTOMER)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("INVALID_ORDER_TRANSITION"));
  }

  @Test
  void 취소_주문없음_404() throws Exception {
    // given
    willThrow(new BusinessException(OrderErrorCode.ORDER_NOT_FOUND))
        .given(orderService)
        .cancelOrder(1L, 99L);

    mockMvc
        .perform(post("/api/v1/orders/99/cancel").with(user(CUSTOMER)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("ORDER_NOT_FOUND"));
  }
}
