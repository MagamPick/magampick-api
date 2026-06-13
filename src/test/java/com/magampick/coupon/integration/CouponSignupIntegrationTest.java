package com.magampick.coupon.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.magampick.TestcontainersConfiguration;
import com.magampick.address.dto.AddressCreateRequest;
import com.magampick.auth.dto.CustomerSignupRequest;
import com.magampick.coupon.domain.CouponKind;
import com.magampick.coupon.domain.CouponStatus;
import com.magampick.coupon.repository.UserCouponRepository;
import com.magampick.customer.repository.CustomerRepository;
import com.magampick.phone.repository.PhoneVerificationStore;
import com.magampick.terms.domain.Term;
import com.magampick.terms.domain.TermRole;
import com.magampick.terms.domain.TermType;
import com.magampick.terms.repository.TermRepository;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소비자 회원가입 핵심 흐름 통합 테스트 — 가입 후 가입 축하 쿠폰 자동 발급 검증. TDD: 가입 완료 후 user_coupons 에 SIGNUP 쿠폰 1건이 USABLE
 * 상태로 저장되어야 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class})
@Transactional
class CouponSignupIntegrationTest {

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @Autowired PhoneVerificationStore phoneVerificationStore;
  @Autowired TermRepository termRepository;
  @Autowired CustomerRepository customerRepository;
  @Autowired UserCouponRepository userCouponRepository;

  private String uniquePhone() {
    long suffix = Math.floorMod(System.nanoTime(), 100_000_000L);
    return "010" + String.format("%08d", suffix);
  }

  /** 실 Redis 기반 본인인증 토큰 발급 + 확인. */
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
    return objectMapper
        .readTree(confirm.getResponse().getContentAsString())
        .path("data")
        .path("verificationToken")
        .asText();
  }

  @Test
  void 소비자_회원가입_완료_후_가입_축하_쿠폰_1건_발급됨() throws Exception {
    // given
    String email = "coupon_signup_" + System.nanoTime() + "@test.com";
    String phone = uniquePhone();
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
            "쿠폰테스터",
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

    // when: 소비자 회원가입 API 호출
    mockMvc
        .perform(
            post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated());

    // then: 해당 소비자에게 가입 축하 쿠폰(SIGNUP, USABLE) 1건이 발급됨
    Long customerId = customerRepository.findByEmail(email).orElseThrow().getId();

    var userCoupons = userCouponRepository.findByCustomerIdWithCoupon(customerId);

    assertThat(userCoupons).hasSize(1);
    assertThat(userCoupons.get(0).getStatus()).isEqualTo(CouponStatus.USABLE);
    assertThat(userCoupons.get(0).getCoupon().getKind()).isEqualTo(CouponKind.SIGNUP);
    assertThat(userCoupons.get(0).getCoupon().getDiscountValue()).isEqualTo(20);
    assertThat(userCoupons.get(0).getExpiresAt()).isNotNull();
  }

  private Set<TermType> customerTermTypes() {
    return Set.of(
        TermType.TERMS_OF_SERVICE,
        TermType.PRIVACY,
        TermType.LOCATION,
        TermType.AGE_14,
        TermType.MARKETING);
  }
}
