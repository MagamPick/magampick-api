package com.magampick.address;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.magampick.TestcontainersConfiguration;
import com.magampick.address.dto.AddressCreateRequest;
import com.magampick.customer.domain.Customer;
import com.magampick.customer.repository.CustomerRepository;
import com.magampick.global.security.JwtProvider;
import com.magampick.global.security.Role;
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
@Import(TestcontainersConfiguration.class)
class AddressIntegrationTest {

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @Autowired CustomerRepository customerRepository;
  @Autowired JwtProvider jwtProvider;

  /** 주소 테스트용 — 가입 흐름을 거치지 않고 주소 0개인 소비자 + access token 을 만든다. */
  private String newCustomerToken() {
    Customer customer =
        customerRepository.save(
            Customer.builder()
                .email("addr_" + System.nanoTime() + "@test.com")
                .passwordHash("x")
                .nickname("nick")
                .build());
    return jwtProvider.issueAccessToken(customer.getId(), Role.CUSTOMER);
  }

  private long createAddress(String token, String label, double lat, double lng) throws Exception {
    AddressCreateRequest request =
        new AddressCreateRequest(label, "서울특별시 강남구 테헤란로 427", null, null, "06158", lat, lng);

    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/customers/me/addresses")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn();

    JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
    return root.path("data").path("id").asLong();
  }

  @Test
  void 주소지_등록_후_목록_조회_default_TRUE_반환() throws Exception {
    // given — 소비자 + 토큰
    String token = newCustomerToken();

    // when — 주소지 1개 등록
    long addressId = createAddress(token, "집", 37.5066, 127.0535);

    // then — 목록 조회 시 default = true
    mockMvc
        .perform(
            get("/api/v1/customers/me/addresses")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].id").value(addressId))
        .andExpect(jsonPath("$.data[0].isDefault").value(true))
        .andExpect(jsonPath("$.data[0].latitude").value(37.5066))
        .andExpect(jsonPath("$.data[0].longitude").value(127.0535));
  }

  @Test
  void 기본_주소지_변경시_기존_default_FALSE_로_unset() throws Exception {
    // given — 소비자 + 주소 2개 등록 (첫 번째가 default)
    String token = newCustomerToken();
    long firstId = createAddress(token, "집", 37.5066, 127.0535);
    long secondId = createAddress(token, "회사", 37.4979, 127.0276);

    // when — 두 번째 주소를 default 로 변경
    mockMvc
        .perform(
            post("/api/v1/customers/me/addresses/" + secondId + "/default")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(secondId))
        .andExpect(jsonPath("$.data.isDefault").value(true));

    // then — 목록 조회: 두 번째가 default, 첫 번째는 not default
    MvcResult listResult =
        mockMvc
            .perform(
                get("/api/v1/customers/me/addresses")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();

    JsonNode data =
        objectMapper.readTree(listResult.getResponse().getContentAsString()).path("data");
    assertThat(data).hasSize(2);
    // 정렬: is_default DESC, created_at ASC → second 가 [0]
    assertThat(data.get(0).path("id").asLong()).isEqualTo(secondId);
    assertThat(data.get(0).path("isDefault").asBoolean()).isTrue();
    assertThat(data.get(1).path("id").asLong()).isEqualTo(firstId);
    assertThat(data.get(1).path("isDefault").asBoolean()).isFalse();
  }

  @Test
  void default_삭제시_가장_오래된_주소_자동_승계() throws Exception {
    // given — 3개 등록 (1번이 default, 시간순)
    String token = newCustomerToken();
    long firstId = createAddress(token, "집", 37.5066, 127.0535);
    long secondId = createAddress(token, "회사", 37.4979, 127.0276);
    long thirdId = createAddress(token, "엄마집", 37.5172, 127.0473);

    // when — default (firstId) 삭제
    mockMvc
        .perform(
            delete("/api/v1/customers/me/addresses/" + firstId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isNoContent());

    // then — 가장 오래된 (secondId) 가 새 default 로 자동 승계
    MvcResult listResult =
        mockMvc
            .perform(
                get("/api/v1/customers/me/addresses")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();

    JsonNode data =
        objectMapper.readTree(listResult.getResponse().getContentAsString()).path("data");
    assertThat(data).hasSize(2);
    // 정렬: is_default DESC, created_at ASC → secondId 가 [0]
    assertThat(data.get(0).path("id").asLong()).isEqualTo(secondId);
    assertThat(data.get(0).path("isDefault").asBoolean()).isTrue();
    assertThat(data.get(1).path("id").asLong()).isEqualTo(thirdId);
    assertThat(data.get(1).path("isDefault").asBoolean()).isFalse();
  }
}
