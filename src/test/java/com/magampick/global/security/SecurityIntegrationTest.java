package com.magampick.global.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.magampick.TestcontainersConfiguration;
import com.magampick.global.support.CrossCuttingTestController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** SecurityConfig + JwtAuthenticationFilter + EntryPoint 의 인증 흐름 통합 검증. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, CrossCuttingTestController.class})
class SecurityIntegrationTest {

  @Autowired MockMvc mockMvc;
  @Autowired JwtProvider jwtProvider;

  @Test
  void 토큰_없이_인증필요_경로_접근_시_401() throws Exception {
    mockMvc
        .perform(get("/test-support/ok"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
  }

  @Test
  void 유효한_토큰으로_인증필요_경로_접근_성공() throws Exception {
    // given
    String token = jwtProvider.issueAccessToken(1L, Role.CUSTOMER);

    // when / then
    mockMvc
        .perform(get("/test-support/ok").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.value").value("hello"));
  }
}
