package com.magampick.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.magampick.TestcontainersConfiguration;
import com.magampick.address.dto.AddressCreateRequest;
import com.magampick.auth.dto.CustomerSignupRequest;
import com.magampick.auth.dto.SellerSignupRequest;
import com.magampick.auth.dto.SocialSignupRequest;
import com.magampick.auth.support.SellerTestSupportController;
import com.magampick.customer.domain.Customer;
import com.magampick.customer.repository.CustomerRepository;
import com.magampick.global.support.CrossCuttingTestController;
import com.magampick.phone.repository.PhoneVerificationStore;
import com.magampick.store.dto.StoreCreateRequest;
import com.magampick.terms.domain.Term;
import com.magampick.terms.domain.TermRole;
import com.magampick.terms.domain.TermType;
import com.magampick.terms.repository.TermRepository;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
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
  @Autowired CustomerRepository customerRepository;
  @Autowired JdbcTemplate jdbcTemplate;

  private String verificationToken(String phone) throws Exception {
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
    return verificationToken;
  }

  /** 본인인증(실 Redis) → 약관 seed → 좌표 주소까지 갖춘 실제 소비자 가입을 수행하고 signup 응답을 반환한다. */
  private MvcResult signupCustomer(String email, String phone) throws Exception {
    String verificationToken = verificationToken(phone);
    List<Long> requiredTermIds =
        termRepository
            .findByRequiredTrueAndTypeInAndRole(customerTermTypes(), TermRole.CUSTOMER)
            .stream()
            .map(Term::getId)
            .toList();

    CustomerSignupRequest request =
        new CustomerSignupRequest(
            email,
            "Abcd1234!",
            "nick",
            phone,
            verificationToken,
            requiredTermIds,
            new AddressCreateRequest(
                "집",
                "서울특별시 강남구 테헤란로 427",
                null,
                "101동 1502호",
                "06158",
                "11680",
                "3179999",
                null,
                null));

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
    MvcResult signupResult = signupCustomer(uniqueEmail, uniquePhone());

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
    String phone = uniquePhone();
    String verificationToken = verificationToken(phone);
    List<Long> requiredTermIds =
        termRepository
            .findByRequiredTrueAndTypeInAndRole(sellerTermTypes(), TermRole.SELLER)
            .stream()
            .map(Term::getId)
            .toList();
    SellerSignupRequest request =
        new SellerSignupRequest(
            uniqueEmail,
            "Abcd1234!",
            "홍길동",
            phone,
            verificationToken,
            requiredTermIds,
            storeRequest("동네빵집"));

    MvcResult signupResult =
        mockMvc
            .perform(
                multipart("/api/v1/auth/seller/signup")
                    .file(requestPart(request))
                    .file(imagePart()))
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
  void 사장_회원가입_약관_실패시_seller_terms_store_전체_롤백() throws Exception {
    String uniqueEmail = "seller_rollback_" + System.nanoTime() + "@test.com";
    String storeName = "롤백빵집_" + System.nanoTime();
    String phone = uniquePhone();
    String verificationToken = verificationToken(phone);
    List<Long> requiredTermIds =
        termRepository
            .findByRequiredTrueAndTypeInAndRole(sellerTermTypes(), TermRole.SELLER)
            .stream()
            .map(Term::getId)
            .toList();
    SellerSignupRequest request =
        new SellerSignupRequest(
            uniqueEmail,
            "Abcd1234!",
            "홍길동",
            phone,
            verificationToken,
            List.of(requiredTermIds.get(0)),
            storeRequest(storeName));

    mockMvc
        .perform(
            multipart("/api/v1/auth/seller/signup").file(requestPart(request)).file(imagePart()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("REQUIRED_TERMS_NOT_AGREED"));

    org.assertj.core.api.Assertions.assertThat(rowCount("sellers", "email", uniqueEmail)).isZero();
    org.assertj.core.api.Assertions.assertThat(rowCount("stores", "name", storeName)).isZero();
    org.assertj.core.api.Assertions.assertThat(sellerAgreementCount(uniqueEmail)).isZero();
  }

  private MockMultipartFile requestPart(Object request) throws Exception {
    return new MockMultipartFile(
        "request",
        "request",
        MediaType.APPLICATION_JSON_VALUE,
        objectMapper.writeValueAsBytes(request));
  }

  private MockMultipartFile imagePart() {
    return new MockMultipartFile("image", "store.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[1024]);
  }

  private String uniquePhone() {
    long suffix = Math.floorMod(System.nanoTime(), 100_000_000L);
    return "010" + String.format("%08d", suffix);
  }

  private StoreCreateRequest storeRequest(String name) {
    return new StoreCreateRequest(
        "123-45-67890",
        "홍길동",
        LocalDate.of(2024, 3, 15),
        name,
        "서울 강남구 테헤란로 427",
        null,
        "1층",
        "06158",
        "0212345678",
        "신선한 빵",
        "11680",
        "3179999");
  }

  private int rowCount(String table, String column, String value) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?", Integer.class, value);
  }

  private int sellerAgreementCount(String email) {
    return jdbcTemplate.queryForObject(
        """
        SELECT COUNT(*)
        FROM seller_terms_agreements sta
        JOIN sellers s ON s.id = sta.seller_id
        WHERE s.email = ?
        """,
        Integer.class,
        email);
  }

  private Set<TermType> customerTermTypes() {
    return Set.of(
        TermType.TERMS_OF_SERVICE,
        TermType.PRIVACY,
        TermType.LOCATION,
        TermType.AGE_14,
        TermType.MARKETING);
  }

  private Set<TermType> sellerTermTypes() {
    return Set.of(
        TermType.TERMS_OF_SERVICE,
        TermType.PRIVACY,
        TermType.LOCATION,
        TermType.AGE_19,
        TermType.MARKETING);
  }

  @Test
  void refresh_쿠키로_갱신_가능하고_로그아웃후_차단() throws Exception {
    String uniqueEmail = "refresh_" + System.nanoTime() + "@test.com";
    MvcResult signupResult = signupCustomer(uniqueEmail, uniquePhone());
    org.junit.jupiter.api.Assertions.assertEquals(201, signupResult.getResponse().getStatus());

    Cookie refreshCookie = refreshCookieOf(signupResult);

    // 쿠키로 갱신 → 새 access. rotation 없으니 같은 쿠키 재사용 가능.
    mockMvc
        .perform(post("/api/v1/auth/refresh").cookie(refreshCookie))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.accessToken").exists());
    mockMvc.perform(post("/api/v1/auth/refresh").cookie(refreshCookie)).andExpect(status().isOk());

    // 로그아웃 → Redis 세션 삭제.
    mockMvc
        .perform(post("/api/v1/auth/logout").cookie(refreshCookie))
        .andExpect(status().isNoContent());

    // 같은 쿠키로 갱신 → REFRESH_INVALID.
    mockMvc
        .perform(post("/api/v1/auth/refresh").cookie(refreshCookie))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("REFRESH_INVALID"));
  }

  @Test
  void 카카오_신규회원_소셜가입_후_재로그인은_기존회원() throws Exception {
    // 1. 카카오 1단계 — 신규 분기 (Mock provider, 인가코드 해시 기반)
    String reqBody =
        "{\"authorizationCode\":\"kakao-code-"
            + System.nanoTime()
            + "\",\"redirectUri\":\"https://app.example/cb\"}";
    MvcResult first =
        mockMvc
            .perform(
                post("/api/v1/auth/kakao").contentType(MediaType.APPLICATION_JSON).content(reqBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("NEW"))
            .andReturn();
    JsonNode data = objectMapper.readTree(first.getResponse().getContentAsString()).path("data");
    String socialToken = data.path("socialToken").asText();
    String kakaoEmail = data.path("email").asText();

    // 2. 본인인증 (실 Redis)
    String phone = uniquePhone();
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

    // 3. 약관 동의 + /signup/social → 201
    List<Long> requiredTermIds =
        termRepository
            .findByRequiredTrueAndTypeInAndRole(customerTermTypes(), TermRole.CUSTOMER)
            .stream()
            .map(Term::getId)
            .toList();
    SocialSignupRequest signupRequest =
        new SocialSignupRequest(
            socialToken,
            "소셜닉",
            phone,
            verificationToken,
            requiredTermIds,
            new AddressCreateRequest(
                "집",
                "서울특별시 강남구 테헤란로 427",
                null,
                "101동 1502호",
                "06158",
                "11680",
                "3179999",
                null,
                null));
    MvcResult signup =
        mockMvc
            .perform(
                post("/api/v1/auth/signup/social")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(signupRequest)))
            .andExpect(status().isCreated())
            .andReturn();
    String accessToken =
        objectMapper
            .readTree(signup.getResponse().getContentAsString())
            .path("data")
            .path("accessToken")
            .asText();

    // 4. 발급 토큰으로 인증 API 접근 + 소셜 계정(password NULL) 검증
    mockMvc
        .perform(get("/test-support/ok").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
        .andExpect(status().isOk());
    Customer customer = customerRepository.findByEmail(kakaoEmail).orElseThrow();
    org.junit.jupiter.api.Assertions.assertNull(customer.getPasswordHash());

    // 5. 같은 카카오로 재로그인 → 기존 회원(EXISTING)
    mockMvc
        .perform(
            post("/api/v1/auth/kakao").contentType(MediaType.APPLICATION_JSON).content(reqBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("EXISTING"))
        .andExpect(jsonPath("$.data.accessToken").exists());
  }

  /** signup/login 응답의 Set-Cookie 헤더에서 refresh 쿠키 값을 추출한다. */
  private Cookie refreshCookieOf(MvcResult result) {
    String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
    org.junit.jupiter.api.Assertions.assertNotNull(setCookie);
    String value = setCookie.substring("refresh_token=".length(), setCookie.indexOf(';'));
    return new Cookie("refresh_token", value);
  }
}
