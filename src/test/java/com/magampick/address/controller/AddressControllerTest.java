package com.magampick.address.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.magampick.address.dto.AddressCreateRequest;
import com.magampick.address.dto.AddressResponse;
import com.magampick.address.dto.AddressReverseGeocodeRequest;
import com.magampick.address.dto.AddressUpdateRequest;
import com.magampick.address.exception.AddressErrorCode;
import com.magampick.address.service.AddressService;
import com.magampick.global.exception.BusinessException;
import com.magampick.global.exception.CommonErrorCode;
import com.magampick.global.security.CustomUserDetails;
import com.magampick.global.security.JwtAccessDeniedHandler;
import com.magampick.global.security.JwtAuthenticationEntryPoint;
import com.magampick.global.security.JwtProvider;
import com.magampick.global.security.Role;
import com.magampick.global.security.SecurityConfig;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AddressController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class AddressControllerTest {

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @MockitoBean AddressService addressService;
  @MockitoBean JwtProvider jwtProvider;

  private static final CustomUserDetails CUSTOMER_USER = new CustomUserDetails(1L, Role.CUSTOMER);
  private static final CustomUserDetails SELLER_USER = new CustomUserDetails(2L, Role.SELLER);

  private AddressResponse stubResponse(long id, boolean isDefault) {
    return new AddressResponse(
        id,
        "집",
        "서울특별시 강남구 테헤란로 427",
        null,
        "101호",
        "06158",
        37.5066,
        127.0535,
        isDefault,
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }

  private AddressCreateRequest validCreateReq() {
    return new AddressCreateRequest(
        "집", "서울특별시 강남구 테헤란로 427", null, "101호", "06158", "11680", "3179999", null, null);
  }

  @Test
  void POST_addresses_201_성공() throws Exception {
    given(addressService.create(eq(1L), any(AddressCreateRequest.class)))
        .willReturn(stubResponse(10L, true));

    mockMvc
        .perform(
            post("/api/v1/customers/me/addresses")
                .with(user(CUSTOMER_USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validCreateReq())))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(10))
        .andExpect(jsonPath("$.data.isDefault").value(true));
  }

  @Test
  void POST_addresses_400_label_누락() throws Exception {
    AddressCreateRequest req =
        new AddressCreateRequest(
            null, "서울특별시 강남구 테헤란로 427", null, null, "06158", "11680", "3179999", null, null);

    mockMvc
        .perform(
            post("/api/v1/customers/me/addresses")
                .with(user(CUSTOMER_USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
  }

  @Test
  void POST_addresses_400_도로명코드_누락() throws Exception {
    AddressCreateRequest req =
        new AddressCreateRequest(
            "집", "서울특별시 강남구 테헤란로 427", null, null, "06158", "11680", null, null, null);

    mockMvc
        .perform(
            post("/api/v1/customers/me/addresses")
                .with(user(CUSTOMER_USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
  }

  @Test
  void POST_addresses_400_zonecode_형식_위반() throws Exception {
    AddressCreateRequest req =
        new AddressCreateRequest(
            "집", "서울특별시 강남구 테헤란로 427", null, null, "ABCDE", "11680", "3179999", null, null);

    mockMvc
        .perform(
            post("/api/v1/customers/me/addresses")
                .with(user(CUSTOMER_USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
  }

  @Test
  void POST_addresses_409_보유_한도_초과() throws Exception {
    given(addressService.create(eq(1L), any(AddressCreateRequest.class)))
        .willThrow(new BusinessException(AddressErrorCode.ADDRESS_LIMIT_EXCEEDED));

    mockMvc
        .perform(
            post("/api/v1/customers/me/addresses")
                .with(user(CUSTOMER_USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validCreateReq())))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("ADDRESS_LIMIT_EXCEEDED"));
  }

  @Test
  void POST_addresses_401_토큰_없음() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/customers/me/addresses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validCreateReq())))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
  }

  @Test
  void POST_addresses_403_ROLE_CUSTOMER_아님() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/customers/me/addresses")
                .with(user(SELLER_USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validCreateReq())))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
  }

  @Test
  void POST_addresses_201_GPS_좌표만_코드_없이_성공() throws Exception {
    given(addressService.create(eq(1L), any(AddressCreateRequest.class)))
        .willReturn(stubResponse(13L, true));

    AddressCreateRequest req =
        new AddressCreateRequest(
            "집", "서울특별시 중구 세종대로 110", null, "5층", "04524", null, null, 37.5665, 126.9780);

    mockMvc
        .perform(
            post("/api/v1/customers/me/addresses")
                .with(user(CUSTOMER_USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(13));
  }

  @Test
  void POST_addresses_400_좌표도_코드도_없음() throws Exception {
    AddressCreateRequest req =
        new AddressCreateRequest(
            "집", "서울특별시 중구 세종대로 110", null, "5층", "04524", null, null, null, null);

    mockMvc
        .perform(
            post("/api/v1/customers/me/addresses")
                .with(user(CUSTOMER_USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
  }

  @Test
  void GET_addresses_200_성공() throws Exception {
    given(addressService.list(1L))
        .willReturn(List.of(stubResponse(1L, true), stubResponse(2L, false)));

    mockMvc
        .perform(get("/api/v1/customers/me/addresses").with(user(CUSTOMER_USER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data[0].id").value(1))
        .andExpect(jsonPath("$.data[0].isDefault").value(true))
        .andExpect(jsonPath("$.data[1].id").value(2));
  }

  @Test
  void PATCH_addresses_200_label_변경() throws Exception {
    given(addressService.update(eq(1L), eq(5L), any(AddressUpdateRequest.class)))
        .willReturn(stubResponse(5L, false));

    AddressUpdateRequest req = new AddressUpdateRequest("회사", null, null, null, null, null, null);

    mockMvc
        .perform(
            patch("/api/v1/customers/me/addresses/5")
                .with(user(CUSTOMER_USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));
  }

  @Test
  void PATCH_addresses_400_주소_변경시_도로명코드_누락() throws Exception {
    AddressUpdateRequest req =
        new AddressUpdateRequest(null, "새 주소", null, null, "06158", "11680", null);

    mockMvc
        .perform(
            patch("/api/v1/customers/me/addresses/5")
                .with(user(CUSTOMER_USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
  }

  @Test
  void PATCH_addresses_403_본인_외() throws Exception {
    given(addressService.update(eq(1L), eq(5L), any(AddressUpdateRequest.class)))
        .willThrow(new BusinessException(CommonErrorCode.FORBIDDEN));

    AddressUpdateRequest req = new AddressUpdateRequest("회사", null, null, null, null, null, null);

    mockMvc
        .perform(
            patch("/api/v1/customers/me/addresses/5")
                .with(user(CUSTOMER_USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
  }

  @Test
  void POST_addresses_default_200_성공() throws Exception {
    given(addressService.markAsDefault(1L, 5L)).willReturn(stubResponse(5L, true));

    mockMvc
        .perform(post("/api/v1/customers/me/addresses/5/default").with(user(CUSTOMER_USER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.isDefault").value(true));
  }

  @Test
  void DELETE_addresses_204_성공() throws Exception {
    willDoNothing().given(addressService).delete(1L, 5L);

    mockMvc
        .perform(delete("/api/v1/customers/me/addresses/5").with(user(CUSTOMER_USER)))
        .andExpect(status().isNoContent());
  }

  @Test
  void DELETE_addresses_409_기본_주소_삭제_차단() throws Exception {
    org.mockito.BDDMockito.willThrow(
            new BusinessException(AddressErrorCode.DEFAULT_ADDRESS_DELETE_BLOCKED))
        .given(addressService)
        .delete(1L, 5L);

    mockMvc
        .perform(delete("/api/v1/customers/me/addresses/5").with(user(CUSTOMER_USER)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("DEFAULT_ADDRESS_DELETE_BLOCKED"));
  }

  @Test
  void DELETE_addresses_404_없음() throws Exception {
    org.mockito.BDDMockito.willThrow(new BusinessException(AddressErrorCode.ADDRESS_NOT_FOUND))
        .given(addressService)
        .delete(1L, 999L);

    mockMvc
        .perform(delete("/api/v1/customers/me/addresses/999").with(user(CUSTOMER_USER)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("ADDRESS_NOT_FOUND"));
  }

  @Test
  void POST_addresses_reverse_geocode_200_성공() throws Exception {
    given(addressService.reverseGeocode(37.5665, 126.9780)).willReturn("서울특별시 중구 세종대로 110");

    mockMvc
        .perform(
            post("/api/v1/customers/me/addresses/reverse-geocode")
                .with(user(CUSTOMER_USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new AddressReverseGeocodeRequest(37.5665, 126.9780))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.roadAddress").value("서울특별시 중구 세종대로 110"));
  }

  @Test
  void POST_addresses_reverse_geocode_400_좌표_범위_초과() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/customers/me/addresses/reverse-geocode")
                .with(user(CUSTOMER_USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new AddressReverseGeocodeRequest(91.0, 126.9780))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
  }
}
