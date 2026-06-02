package com.magampick.store.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import com.magampick.store.domain.OperationStatus;
import com.magampick.store.dto.BusinessVerificationRequest;
import com.magampick.store.dto.OperationStatusResponse;
import com.magampick.store.dto.OperationStatusTransitionRequest;
import com.magampick.store.dto.StoreCreateRequest;
import com.magampick.store.dto.StoreDetailResponse;
import com.magampick.store.dto.StoreRegisterResponse;
import com.magampick.store.dto.StoreResponse;
import com.magampick.store.exception.StoreErrorCode;
import com.magampick.store.service.StoreService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StoreController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class StoreControllerTest {

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @MockitoBean StoreService storeService;
  @MockitoBean JwtProvider jwtProvider;

  private static final CustomUserDetails SELLER_USER = new CustomUserDetails(1L, Role.SELLER);
  private static final CustomUserDetails CUSTOMER_USER = new CustomUserDetails(2L, Role.CUSTOMER);
  private static final String OWNER_NAME = "홍길동";
  private static final LocalDate OPEN_DATE = LocalDate.of(2024, 3, 15);

  private MockMultipartFile requestPart(String json) {
    return new MockMultipartFile(
        "request", "request", MediaType.APPLICATION_JSON_VALUE, json.getBytes());
  }

  private MockMultipartFile imagePart() {
    return new MockMultipartFile("image", "test.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[1024]);
  }

  private String validRequestJson() throws Exception {
    return objectMapper.writeValueAsString(
        new StoreCreateRequest(
            "123-45-67890",
            OWNER_NAME,
            OPEN_DATE,
            "동네빵집",
            "서울 강남구 테헤란로 427",
            null,
            null,
            "06158",
            "0212345678",
            "신선한 빵"));
  }

  private String invalidRequestJson() throws Exception {
    return objectMapper.writeValueAsString(
        new StoreCreateRequest(
            "123-45-67890",
            OWNER_NAME,
            OPEN_DATE,
            "",
            "서울 강남구 테헤란로 427",
            null,
            null,
            "06158",
            "0212345678",
            null));
  }

  private String verifyRequestJson() throws Exception {
    return objectMapper.writeValueAsString(
        new BusinessVerificationRequest("123-45-67890", OWNER_NAME, OPEN_DATE));
  }

  private String invalidVerifyRequestJson() throws Exception {
    // representativeName 누락
    return objectMapper.writeValueAsString(
        new BusinessVerificationRequest("123-45-67890", "", OPEN_DATE));
  }

  private StoreResponse stubStoreResponse() {
    return new StoreResponse(
        1L,
        "동네빵집",
        "서울 강남구 테헤란로 427",
        null,
        "0212345678",
        "/uploads/uuid.jpg",
        OperationStatus.CLOSED_TODAY,
        OffsetDateTime.now());
  }

  // ── POST /api/v1/seller/stores/business-verification ──────────────────────

  @Test
  void POST_business_verification_204_성공() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/seller/stores/business-verification")
                .contentType(MediaType.APPLICATION_JSON)
                .content(verifyRequestJson())
                .with(user(SELLER_USER)))
        .andExpect(status().isNoContent());
  }

  @Test
  void POST_business_verification_400_검증_실패() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/seller/stores/business-verification")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidVerifyRequestJson())
                .with(user(SELLER_USER)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
  }

  @Test
  void POST_business_verification_400_불일치() throws Exception {
    willThrow(new BusinessException(StoreErrorCode.BUSINESS_INFO_MISMATCH))
        .given(storeService)
        .verifyBusiness(any());

    mockMvc
        .perform(
            post("/api/v1/seller/stores/business-verification")
                .contentType(MediaType.APPLICATION_JSON)
                .content(verifyRequestJson())
                .with(user(SELLER_USER)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("BUSINESS_INFO_MISMATCH"));
  }

  @Test
  void POST_business_verification_401_미인증() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/seller/stores/business-verification")
                .contentType(MediaType.APPLICATION_JSON)
                .content(verifyRequestJson()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void POST_business_verification_403_소비자_접근_거부() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/seller/stores/business-verification")
                .contentType(MediaType.APPLICATION_JSON)
                .content(verifyRequestJson())
                .with(user(CUSTOMER_USER)))
        .andExpect(status().isForbidden());
  }

  // ── POST /api/v1/seller/stores ─────────────────────────────────────────────

  @Test
  void POST_stores_201_성공() throws Exception {
    given(storeService.registerStore(eq(1L), any(), any()))
        .willReturn(new StoreRegisterResponse(1L, OperationStatus.CLOSED_TODAY));

    mockMvc
        .perform(
            multipart("/api/v1/seller/stores")
                .file(requestPart(validRequestJson()))
                .file(imagePart())
                .with(user(SELLER_USER)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.storeId").value(1))
        .andExpect(jsonPath("$.data.operationStatus").value("CLOSED_TODAY"));
  }

  @Test
  void POST_stores_400_검증_실패() throws Exception {
    mockMvc
        .perform(
            multipart("/api/v1/seller/stores")
                .file(requestPart(invalidRequestJson()))
                .file(imagePart())
                .with(user(SELLER_USER)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
  }

  @Test
  void POST_stores_401_미인증() throws Exception {
    mockMvc
        .perform(
            multipart("/api/v1/seller/stores")
                .file(requestPart(validRequestJson()))
                .file(imagePart()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void POST_stores_403_소비자_접근_거부() throws Exception {
    mockMvc
        .perform(
            multipart("/api/v1/seller/stores")
                .file(requestPart(validRequestJson()))
                .file(imagePart())
                .with(user(CUSTOMER_USER)))
        .andExpect(status().isForbidden());
  }

  // ── GET /api/v1/seller/stores ──────────────────────────────────────────────

  @Test
  void GET_stores_200_목록() throws Exception {
    given(storeService.getMyStores(1L)).willReturn(List.of(stubStoreResponse()));

    mockMvc
        .perform(get("/api/v1/seller/stores").with(user(SELLER_USER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].id").value(1))
        .andExpect(jsonPath("$.data[0].name").value("동네빵집"))
        .andExpect(jsonPath("$.data[0].operationStatus").value("CLOSED_TODAY"));
  }

  // ── GET /api/v1/seller/stores/{id} ────────────────────────────────────────

  @Test
  void GET_stores_id_200_상세() throws Exception {
    StoreDetailResponse detail =
        new StoreDetailResponse(
            1L,
            "1234567890",
            "동네빵집",
            "서울 강남구",
            null,
            null,
            "06158",
            37.5,
            127.0,
            "0212345678",
            "소개",
            "/uploads/uuid.jpg",
            OffsetDateTime.now());
    given(storeService.getMyStore(1L, 1L)).willReturn(detail);

    mockMvc
        .perform(get("/api/v1/seller/stores/1").with(user(SELLER_USER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(1))
        .andExpect(jsonPath("$.data.businessNumber").value("1234567890"));
  }

  @Test
  void GET_stores_id_403_타인매장() throws Exception {
    given(storeService.getMyStore(1L, 99L))
        .willThrow(new BusinessException(StoreErrorCode.STORE_ACCESS_DENIED));

    mockMvc
        .perform(get("/api/v1/seller/stores/99").with(user(SELLER_USER)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("STORE_ACCESS_DENIED"));
  }

  // ── GET /api/v1/seller/stores/{id}/operation-status ────────────────────────

  private OperationStatusResponse stubOpStatus(OperationStatus s, boolean canOpen) {
    return new OperationStatusResponse(1L, s, canOpen, canOpen ? LocalTime.of(21, 0) : null);
  }

  @Test
  void GET_operation_status_200_조회_성공() throws Exception {
    given(storeService.getOperationStatus(1L, 1L))
        .willReturn(stubOpStatus(OperationStatus.CLOSED_TODAY, true));

    mockMvc
        .perform(get("/api/v1/seller/stores/1/operation-status").with(user(SELLER_USER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.storeId").value(1))
        .andExpect(jsonPath("$.data.operationStatus").value("CLOSED_TODAY"))
        .andExpect(jsonPath("$.data.canOpenToday").value(true))
        .andExpect(jsonPath("$.data.todayCloseTime").value("21:00:00"));
  }

  @Test
  void GET_operation_status_401_미인증() throws Exception {
    mockMvc
        .perform(get("/api/v1/seller/stores/1/operation-status"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void GET_operation_status_403_소비자() throws Exception {
    mockMvc
        .perform(get("/api/v1/seller/stores/1/operation-status").with(user(CUSTOMER_USER)))
        .andExpect(status().isForbidden());
  }

  @Test
  void GET_operation_status_403_타인매장() throws Exception {
    given(storeService.getOperationStatus(1L, 99L))
        .willThrow(new BusinessException(StoreErrorCode.STORE_ACCESS_DENIED));

    mockMvc
        .perform(get("/api/v1/seller/stores/99/operation-status").with(user(SELLER_USER)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("STORE_ACCESS_DENIED"));
  }

  // ── PATCH /api/v1/seller/stores/{id}/operation-status ─────────────────────

  private String transitionJson(OperationStatus to) throws Exception {
    return objectMapper.writeValueAsString(new OperationStatusTransitionRequest(to));
  }

  @Test
  void PATCH_operation_status_200_전환_성공() throws Exception {
    given(storeService.transitionOperationStatus(eq(1L), eq(1L), eq(OperationStatus.OPEN)))
        .willReturn(stubOpStatus(OperationStatus.OPEN, true));

    mockMvc
        .perform(
            patch("/api/v1/seller/stores/1/operation-status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(transitionJson(OperationStatus.OPEN))
                .with(user(SELLER_USER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.operationStatus").value("OPEN"))
        .andExpect(jsonPath("$.data.canOpenToday").value(true));
  }

  @Test
  void PATCH_operation_status_400_검증_실패() throws Exception {
    // to 누락 (null) → @NotNull 위반
    mockMvc
        .perform(
            patch("/api/v1/seller/stores/1/operation-status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(user(SELLER_USER)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
  }

  @Test
  void PATCH_operation_status_401_미인증() throws Exception {
    mockMvc
        .perform(
            patch("/api/v1/seller/stores/1/operation-status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(transitionJson(OperationStatus.OPEN)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void PATCH_operation_status_403_소비자() throws Exception {
    mockMvc
        .perform(
            patch("/api/v1/seller/stores/1/operation-status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(transitionJson(OperationStatus.OPEN))
                .with(user(CUSTOMER_USER)))
        .andExpect(status().isForbidden());
  }

  @Test
  void PATCH_operation_status_409_오늘_휴무() throws Exception {
    willThrow(new BusinessException(StoreErrorCode.STORE_CLOSED_TODAY))
        .given(storeService)
        .transitionOperationStatus(any(), any(), any());

    mockMvc
        .perform(
            patch("/api/v1/seller/stores/1/operation-status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(transitionJson(OperationStatus.OPEN))
                .with(user(SELLER_USER)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("STORE_CLOSED_TODAY"));
  }

  @Test
  void PATCH_operation_status_409_금지_전이() throws Exception {
    willThrow(new BusinessException(StoreErrorCode.INVALID_STATE_TRANSITION))
        .given(storeService)
        .transitionOperationStatus(any(), any(), any());

    mockMvc
        .perform(
            patch("/api/v1/seller/stores/1/operation-status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(transitionJson(OperationStatus.BREAK))
                .with(user(SELLER_USER)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("INVALID_STATE_TRANSITION"));
  }
}
