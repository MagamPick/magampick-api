package com.magampick.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.magampick.TestcontainersConfiguration;
import com.magampick.address.dto.AddressCreateRequest;
import com.magampick.auth.dto.CustomerSignupRequest;
import com.magampick.auth.dto.RefreshTokenRequest;
import com.magampick.auth.dto.SellerSignupRequest;
import com.magampick.auth.support.SellerTestSupportController;
import com.magampick.global.support.CrossCuttingTestController;
import com.magampick.phone.repository.PhoneVerificationStore;
import com.magampick.terms.domain.Term;
import com.magampick.terms.repository.TermRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@Import({
  TestcontainersConfiguration.class,
  CrossCuttingTestController.class,
  SellerTestSupportController.class
})
class AuthIntegrationTest {

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @Autowired PhoneVerificationStore phoneVerificationStore;
  @Autowired TermRepository termRepository;

  /** 본인인증(실 Redis) → 약관 seed → 좌표 주소까지 갖춘 실제 소비자 가입을 수행하고 signup 응답을 반환한다. */
  private MvcResult signupCustomer(String email, String phone) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/phone-verifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"" + phone + "\"}"))
        .andExpect(status().isOk());

    String digits = phone.replaceAll("[^0-9]", "");
    String code = phoneVerificationStore.findOtpCode(digits).orElseThrow();

    MvcResult confirm =
        mockMvc
            .perform(
                post("/api/v1/auth/phone-verifications/confirm")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"phone\":\"" + phone + "\",\"code\":\"" + code + "\"}"))
            .andExpect(status().isOk())
            .andReturn();
    String verificationToken =
        objectMapper
            .readTree(confirm.getResponse().getContentAsString())
            .path("data")
            .path("verificationToken")
            .asText();

    List<Long> requiredTermIds =
        termRepository.findByRequiredTrue().stream().map(Term::getId).toList();

    CustomerSignupRequest request =
        new CustomerSignupRequest(
            email,
            "Abcd1234!",
            "nick",
            phone,
            verificationToken,
            requiredTermIds,
            new AddressCreateRequest(
                "집", "서울특별시 강남구 테헤란로 427", null, "101동 1502호", "06158", 37.5066, 127.0535));

    return mockMvc
        .perform(
            post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andReturn();
  }

  @Test
  void 소비자_회원가입_후_발급받은_토큰으로_인증필요_API_접근_성공() throws Exception {
    String uniqueEmail = "customer_" + System.nanoTime() + "@test.com";
    MvcResult signupResult = signupCustomer(uniqueEmail, "010-1111-2222");

    org.junit.jupiter.api.Assertions.assertEquals(201, signupResult.getResponse().getStatus());
    JsonNode root = objectMapper.readTree(signupResult.getResponse().getContentAsString());
    String accessToken = root.path("data").path("accessToken").asText();

    mockMvc
        .perform(get("/test-support/ok").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.value").value("hello"));
  }

  @Test
  void 사장_회원가입_후_발급받은_토큰으로_seller_API_접근_성공() throws Exception {
    String uniqueEmail = "seller_" + System.nanoTime() + "@test.com";
    SellerSignupRequest request =
        new SellerSignupRequest(uniqueEmail, "Abcd1234!", "owner", "1234567890");

    MvcResult signupResult =
        mockMvc
            .perform(
                post("/api/v1/auth/seller/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andReturn();

    JsonNode root = objectMapper.readTree(signupResult.getResponse().getContentAsString());
    String accessToken = root.path("data").path("accessToken").asText();

    mockMvc
        .perform(
            get("/api/v1/seller/test-support/ping")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.value").value("ok"));
  }

  @Test
  void refresh_token_갱신시_기존_refresh_token은_재사용_불가() throws Exception {
    String uniqueEmail = "refresh_" + System.nanoTime() + "@test.com";
    MvcResult signupResult = signupCustomer(uniqueEmail, "010-3333-4444");

    org.junit.jupiter.api.Assertions.assertEquals(201, signupResult.getResponse().getStatus());
    JsonNode signupRoot = objectMapper.readTree(signupResult.getResponse().getContentAsString());
    String oldRefreshToken = signupRoot.path("data").path("refreshToken").asText();

    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RefreshTokenRequest(oldRefreshToken))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RefreshTokenRequest(oldRefreshToken))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));
  }
}
