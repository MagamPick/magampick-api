package com.magampick.phone.service;

import com.magampick.global.exception.BusinessException;
import com.magampick.phone.exception.PhoneVerificationErrorCode;
import com.magampick.phone.repository.PhoneVerificationStore;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 휴대폰 본인인증(SMS OTP) 공통 모듈. 인증번호 발송·검증, 본인인증 토큰 발급/소비, 발송·시도 제한을 담당한다 (노션 본인인증 명세 / ADR-001). 단기
 * 데이터는 Redis({@link PhoneVerificationStore})로 관리해 TTL·1회용을 강제한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PhoneVerificationService {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");
  private static final Pattern PHONE_PATTERN = Pattern.compile("^010\\d{8}$");
  private static final int DAILY_SEND_LIMIT = 10;
  private static final int MAX_VERIFY_ATTEMPTS = 5;
  private static final String MOCK_BYPASS_CODE = "000000";

  private final PhoneVerificationStore store;
  private final SmsSender smsSender;
  private final VerificationCodeGenerator codeGenerator;
  private final SmsConfig smsConfig;

  /** 인증번호 발송. 형식·일일 한도·재발송 쿨다운 검사 후 SMS 발송하고 OTP 를 저장한다. */
  public void requestCode(String rawPhone) {
    String phone = normalize(rawPhone);
    LocalDate today = LocalDate.now(KST);

    if (store.currentDailyCount(phone, today) >= DAILY_SEND_LIMIT) {
      throw new BusinessException(PhoneVerificationErrorCode.OTP_DAILY_LIMIT);
    }
    if (!store.tryAcquireResendCooldown(phone)) {
      throw new BusinessException(PhoneVerificationErrorCode.OTP_RESEND_LIMIT);
    }

    String code = codeGenerator.generate();
    try {
      smsSender.sendVerificationCode(phone, code);
    } catch (RuntimeException e) {
      store.releaseResendCooldown(phone); // 발송 실패 → 즉시 재시도 허용
      throw new BusinessException(PhoneVerificationErrorCode.SMS_SEND_FAILED, e);
    }

    store.saveOtp(phone, code);
    store.incrementDailyCount(phone, today);
    log.info("본인인증 번호 발송. phone={}", phone);
  }

  /** 인증번호 검증. 성공 시 OTP 를 소비하고 본인인증 토큰(15분)을 발급한다. mock 모드에서 000000 입력 시 OTP 없이 토큰 발급. */
  public String verifyCode(String rawPhone, String code) {
    String phone = normalize(rawPhone);

    if (smsConfig.isMockEnabled() && MOCK_BYPASS_CODE.equals(code)) {
      String token = store.issueToken(phone);
      log.info("본인인증 mock 우회(000000). phone={}", phone);
      return token;
    }

    String savedCode =
        store
            .findOtpCode(phone)
            .orElseThrow(() -> new BusinessException(PhoneVerificationErrorCode.OTP_EXPIRED));

    if (!savedCode.equals(code)) {
      long attempts = store.incrementOtpAttempts(phone);
      if (attempts >= MAX_VERIFY_ATTEMPTS) {
        store.deleteOtp(phone);
        throw new BusinessException(PhoneVerificationErrorCode.OTP_ATTEMPT_LIMIT);
      }
      throw new BusinessException(PhoneVerificationErrorCode.OTP_INVALID);
    }

    store.deleteOtp(phone);
    String token = store.issueToken(phone);
    log.info("본인인증 성공, 토큰 발급. phone={}", phone);
    return token;
  }

  /**
   * 본인인증 토큰 소비(1회용). 회원가입·비밀번호 재설정 등 호출 측에서 사용하며, 토큰이 발급된 번호와 사용 시점 번호가 일치해야 한다. 성공 시 토큰을 무효화하고 검증된
   * 번호를 반환한다.
   */
  public String consumeVerificationToken(String token, String rawPhone) {
    String phone = normalize(rawPhone);
    String verifiedPhone =
        store
            .findTokenPhone(token)
            .orElseThrow(
                () -> new BusinessException(PhoneVerificationErrorCode.PHONE_VERIFICATION_EXPIRED));
    if (!verifiedPhone.equals(phone)) {
      throw new BusinessException(PhoneVerificationErrorCode.PHONE_VERIFICATION_EXPIRED);
    }
    store.deleteToken(token);
    return verifiedPhone;
  }

  private String normalize(String rawPhone) {
    String digits = rawPhone == null ? "" : rawPhone.replaceAll("[^0-9]", "");
    if (!PHONE_PATTERN.matcher(digits).matches()) {
      throw new BusinessException(PhoneVerificationErrorCode.PHONE_FORMAT_INVALID);
    }
    return digits;
  }
}
