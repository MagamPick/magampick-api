package com.magampick.global.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.magampick.global.support.CrossCuttingTestController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** ApiResponseAdvice (응답 wrap) + GlobalExceptionHandler (예외 변환) 동작 검증. 보안 필터는 무관하므로 비활성화. */
@WebMvcTest(CrossCuttingTestController.class)
@AutoConfigureMockMvc(addFilters = false)
class CrossCuttingWebMvcTest {

  @Autowired MockMvc mockMvc;

  @Test
  void 정상_응답_success_envelope_로_wrap() throws Exception {
    mockMvc
        .perform(get("/test-support/ok"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.value").value("hello"))
        .andExpect(jsonPath("$.error").doesNotExist());
  }

  @Test
  void BusinessException_발생_시_에러_envelope_와_상태코드() throws Exception {
    mockMvc
        .perform(get("/test-support/business-error"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
        .andExpect(jsonPath("$.data").doesNotExist());
  }

  @Test
  void 검증_실패_시_400_INVALID_INPUT_과_fieldErrors() throws Exception {
    mockMvc
        .perform(
            post("/test-support/validate").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
        .andExpect(jsonPath("$.error.fieldErrors[0].field").value("value"));
  }

  @Test
  void 미처리_예외_시_500_INTERNAL_ERROR() throws Exception {
    mockMvc
        .perform(get("/test-support/unexpected-error"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"));
  }
}
