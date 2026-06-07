package com.magampick.phone.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.magampick.global.exception.BusinessException;
import com.magampick.phone.exception.PhoneVerificationErrorCode;
import com.magampick.phone.repository.PhoneVerificationStore;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PhoneVerificationServiceTest {

  private static final String RAW_PHONE = "010-1234-5678";
  private static final String PHONE = "01012345678";
  private static final String CODE = "123456";

  @Mock private PhoneVerificationStore store;
  @Mock private SmsSender smsSender;
  @Mock private VerificationCodeGenerator codeGenerator;
  @Mock private SmsConfig smsConfig;

  @InjectMocks private PhoneVerificationService service;

  @Test
  void 발송_성공_시_OTP_저장하고_SMS_발송한다() {
    // given
    given(store.tryAcquireResendCooldown(PHONE)).willReturn(true);
    given(codeGenerator.generate()).willReturn(CODE);

    // when
    service.requestCode(RAW_PHONE);

    // then — 정규화된 번호로 SMS 발송 + OTP 저장 + 일일 카운트 증가
    verify(smsSender).sendVerificationCode(PHONE, CODE);
    verify(store).saveOtp(PHONE, CODE);
    verify(store).incrementDailyCount(eq(PHONE), any(LocalDate.class));
  }

  @Test
  void 휴대폰_형식_위반_시_PHONE_FORMAT_INVALID() {
    // when & then
    assertThatThrownBy(() -> service.requestCode("0101234"))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(PhoneVerificationErrorCode.PHONE_FORMAT_INVALID);
  }

  @Test
  void 일일_발송_한도_초과_시_OTP_DAILY_LIMIT() {
    // given
    given(store.currentDailyCount(eq(PHONE), any(LocalDate.class))).willReturn(10L);

    // when & then — 한도 초과면 쿨다운/발송에 도달하지 않는다
    assertThatThrownBy(() -> service.requestCode(RAW_PHONE))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(PhoneVerificationErrorCode.OTP_DAILY_LIMIT);
    verify(smsSender, never()).sendVerificationCode(any(), any());
  }

  @Test
  void 재발송_쿨다운_시_OTP_RESEND_LIMIT() {
    // given
    given(store.tryAcquireResendCooldown(PHONE)).willReturn(false);

    // when & then
    assertThatThrownBy(() -> service.requestCode(RAW_PHONE))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(PhoneVerificationErrorCode.OTP_RESEND_LIMIT);
    verify(store, never()).saveOtp(any(), any());
  }

  @Test
  void SMS_발송_실패_시_SMS_SEND_FAILED_그리고_쿨다운_해제() {
    // given
    given(store.tryAcquireResendCooldown(PHONE)).willReturn(true);
    given(codeGenerator.generate()).willReturn(CODE);
    willThrow(new RuntimeException("SOLAPI down"))
        .given(smsSender)
        .sendVerificationCode(PHONE, CODE);

    // when & then
    assertThatThrownBy(() -> service.requestCode(RAW_PHONE))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(PhoneVerificationErrorCode.SMS_SEND_FAILED);
    verify(store).releaseResendCooldown(PHONE);
    verify(store, never()).saveOtp(any(), any());
  }

  @Test
  void 검증_성공_시_본인인증_토큰_발급하고_OTP_삭제() {
    // given
    given(store.findOtpCode(PHONE)).willReturn(Optional.of(CODE));
    given(store.issueToken(PHONE)).willReturn("token-123");

    // when
    String token = service.verifyCode(RAW_PHONE, CODE);

    // then
    assertThat(token).isEqualTo("token-123");
    verify(store).deleteOtp(PHONE);
  }

  @Test
  void OTP_없으면_OTP_EXPIRED() {
    // given
    given(store.findOtpCode(PHONE)).willReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> service.verifyCode(RAW_PHONE, CODE))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(PhoneVerificationErrorCode.OTP_EXPIRED);
  }

  @Test
  void 잘못된_코드_시_OTP_INVALID_그리고_시도_증가() {
    // given
    given(store.findOtpCode(PHONE)).willReturn(Optional.of(CODE));
    given(store.incrementOtpAttempts(PHONE)).willReturn(2L);

    // when & then
    assertThatThrownBy(() -> service.verifyCode(RAW_PHONE, "000000"))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(PhoneVerificationErrorCode.OTP_INVALID);
    verify(store).incrementOtpAttempts(PHONE);
    verify(store, never()).deleteOtp(PHONE);
  }

  @Test
  void 시도_5회_도달_시_OTP_ATTEMPT_LIMIT_그리고_무효화() {
    // given
    given(store.findOtpCode(PHONE)).willReturn(Optional.of(CODE));
    given(store.incrementOtpAttempts(PHONE)).willReturn(5L);

    // when & then
    assertThatThrownBy(() -> service.verifyCode(RAW_PHONE, "000000"))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(PhoneVerificationErrorCode.OTP_ATTEMPT_LIMIT);
    verify(store).deleteOtp(PHONE);
  }

  @Test
  void mock_모드에서_000000_입력하면_OTP_없이_토큰_발급() {
    // given
    given(smsConfig.isMockEnabled()).willReturn(true);
    given(store.issueToken(PHONE)).willReturn("token-mock");

    // when
    String token = service.verifyCode(RAW_PHONE, "000000");

    // then — bypass 는 OTP 조회 없이 바로 토큰 발급
    assertThat(token).isEqualTo("token-mock");
    verify(store, never()).findOtpCode(any());
    verify(store).issueToken(PHONE);
  }

  @Test
  void mock_꺼지면_000000은_일반_OTP_검증으로_처리() {
    // given — smsConfig mock 기본값 false → bypass 미적용
    given(store.findOtpCode(PHONE)).willReturn(Optional.of(CODE));
    given(store.incrementOtpAttempts(PHONE)).willReturn(1L);

    // when & then — 저장된 CODE 와 다르므로 OTP_INVALID
    assertThatThrownBy(() -> service.verifyCode(RAW_PHONE, "000000"))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(PhoneVerificationErrorCode.OTP_INVALID);
  }

  @Test
  void 토큰_소비_성공_시_검증된_번호_반환하고_토큰_삭제() {
    // given
    given(store.findTokenPhone("token-123")).willReturn(Optional.of(PHONE));

    // when
    String verifiedPhone = service.consumeVerificationToken("token-123", RAW_PHONE);

    // then
    assertThat(verifiedPhone).isEqualTo(PHONE);
    verify(store).deleteToken("token-123");
  }

  @Test
  void 만료_토큰_사용_시_PHONE_VERIFICATION_EXPIRED() {
    // given
    given(store.findTokenPhone("token-123")).willReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> service.consumeVerificationToken("token-123", RAW_PHONE))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(PhoneVerificationErrorCode.PHONE_VERIFICATION_EXPIRED);
  }

  @Test
  void 토큰_번호_불일치_시_PHONE_VERIFICATION_EXPIRED() {
    // given — 토큰은 다른 번호로 발급됨
    given(store.findTokenPhone("token-123")).willReturn(Optional.of("01099998888"));

    // when & then
    assertThatThrownBy(() -> service.consumeVerificationToken("token-123", RAW_PHONE))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(PhoneVerificationErrorCode.PHONE_VERIFICATION_EXPIRED);
    verify(store, never()).deleteToken(any());
  }
}
