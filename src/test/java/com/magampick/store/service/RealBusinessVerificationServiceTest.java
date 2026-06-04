package com.magampick.store.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.magampick.global.exception.BusinessException;
import com.magampick.store.exception.StoreErrorCode;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RealBusinessVerificationServiceTest {

  private static final String VALIDATE_URL =
      "https://api.odcloud.kr/api/nts-businessman/v1/validate";
  private static final String SERVICE_KEY = "test-service-key";
  private static final NtsBusinessProperties PROPS =
      new NtsBusinessProperties(SERVICE_KEY, VALIDATE_URL);
  private static final String REQUEST_URL = VALIDATE_URL + "?serviceKey=" + SERVICE_KEY;

  private static final String BUSINESS_NUMBER = "1234567890";
  private static final String OWNER = "홍길동";
  private static final LocalDate OPEN_DATE = LocalDate.of(2024, 3, 15);

  private MockRestServiceServer server;

  private RealBusinessVerificationService serviceWith() {
    RestClient.Builder builder = RestClient.builder();
    server = MockRestServiceServer.bindTo(builder).build();
    return new RealBusinessVerificationService(PROPS, builder.build());
  }

  private void expectValidateAndRespond(String responseJson) {
    server
        .expect(requestTo(REQUEST_URL))
        .andExpect(method(HttpMethod.POST))
        .andExpect(jsonPath("$.businesses[0].b_no").value(BUSINESS_NUMBER))
        .andExpect(jsonPath("$.businesses[0].start_dt").value("20240315"))
        .andExpect(jsonPath("$.businesses[0].p_nm").value(OWNER))
        .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));
  }

  @Test
  void 계속사업자_3요소_일치_통과() {
    // given — valid=01(일치) + b_stt_cd=01(계속사업자)
    RealBusinessVerificationService service = serviceWith();
    expectValidateAndRespond("{\"data\":[{\"valid\":\"01\",\"status\":{\"b_stt_cd\":\"01\"}}]}");

    // when / then — 예외 없이 통과 + 요청 바디(번호·개업일 yyyyMMdd·대표자명) 검증
    assertThatCode(() -> service.verify(BUSINESS_NUMBER, OWNER, OPEN_DATE))
        .doesNotThrowAnyException();
    server.verify();
  }

  @Test
  void 휴업자_정상영업_아님_예외() {
    // given — b_stt_cd=02(휴업자)
    RealBusinessVerificationService service = serviceWith();
    expectValidateAndRespond("{\"data\":[{\"valid\":\"01\",\"status\":{\"b_stt_cd\":\"02\"}}]}");

    // when / then
    assertThatThrownBy(() -> service.verify(BUSINESS_NUMBER, OWNER, OPEN_DATE))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", StoreErrorCode.BUSINESS_NUMBER_NOT_ACTIVE);
  }

  @Test
  void 폐업자_정상영업_아님_예외() {
    // given — b_stt_cd=03(폐업자)
    RealBusinessVerificationService service = serviceWith();
    expectValidateAndRespond("{\"data\":[{\"valid\":\"01\",\"status\":{\"b_stt_cd\":\"03\"}}]}");

    // when / then
    assertThatThrownBy(() -> service.verify(BUSINESS_NUMBER, OWNER, OPEN_DATE))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", StoreErrorCode.BUSINESS_NUMBER_NOT_ACTIVE);
  }

  @Test
  void 미등록_사업자번호_정상영업_아님_예외() {
    // given — 미등록: status.b_stt_cd 공백
    RealBusinessVerificationService service = serviceWith();
    expectValidateAndRespond("{\"data\":[{\"valid\":\"02\",\"status\":{\"b_stt_cd\":\"\"}}]}");

    // when / then
    assertThatThrownBy(() -> service.verify(BUSINESS_NUMBER, OWNER, OPEN_DATE))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", StoreErrorCode.BUSINESS_NUMBER_NOT_ACTIVE);
  }

  @Test
  void 진위확인_불일치_예외() {
    // given — 등록·정상영업(b_stt_cd=01)이지만 대표자명/개업일자 불일치(valid=02)
    RealBusinessVerificationService service = serviceWith();
    expectValidateAndRespond("{\"data\":[{\"valid\":\"02\",\"status\":{\"b_stt_cd\":\"01\"}}]}");

    // when / then
    assertThatThrownBy(() -> service.verify(BUSINESS_NUMBER, OWNER, OPEN_DATE))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", StoreErrorCode.BUSINESS_INFO_MISMATCH);
  }

  @Test
  void 국세청_API_장애_검증실패_예외() {
    // given — 국세청 API 5xx
    RealBusinessVerificationService service = serviceWith();
    server.expect(requestTo(REQUEST_URL)).andRespond(withServerError());

    // when / then
    assertThatThrownBy(() -> service.verify(BUSINESS_NUMBER, OWNER, OPEN_DATE))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue(
            "errorCode", StoreErrorCode.BUSINESS_NUMBER_VERIFICATION_FAILED);
  }
}
