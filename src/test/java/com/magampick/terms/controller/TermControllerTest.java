package com.magampick.terms.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.magampick.terms.domain.TermType;
import com.magampick.terms.dto.TermResponse;
import com.magampick.terms.service.TermService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TermController.class)
@AutoConfigureMockMvc(addFilters = false)
class TermControllerTest {

  @Autowired MockMvc mockMvc;

  @MockitoBean TermService termService;

  @Test
  void 약관_목록_조회_200() throws Exception {
    // given
    given(termService.getTermsForSignup())
        .willReturn(
            List.of(
                new TermResponse(1L, TermType.TERMS_OF_SERVICE, 1, "서비스 이용약관", "본문", true),
                new TermResponse(5L, TermType.MARKETING, 1, "마케팅 정보 수신 동의", "본문", false)));

    // when & then
    mockMvc
        .perform(get("/api/v1/terms"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.length()").value(2))
        .andExpect(jsonPath("$.data[0].type").value("TERMS_OF_SERVICE"))
        .andExpect(jsonPath("$.data[0].required").value(true))
        .andExpect(jsonPath("$.data[1].type").value("MARKETING"))
        .andExpect(jsonPath("$.data[1].required").value(false));
  }
}
