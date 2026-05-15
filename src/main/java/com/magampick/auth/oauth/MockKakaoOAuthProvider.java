package com.magampick.auth.oauth;

import com.magampick.auth.domain.OAuthProviderType;
import com.magampick.global.exception.BusinessException;
import com.magampick.global.exception.CommonErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.stereotype.Component;

/** 실제 카카오 API 연동 전까지 사용하는 mock provider. */
@Component
public class MockKakaoOAuthProvider implements OAuthProvider {

  @Override
  public OAuthProviderType providerType() {
    return OAuthProviderType.KAKAO;
  }

  @Override
  public OAuthUserInfo getUserInfo(String accessToken) {
    String suffix = sha256Hex(accessToken).substring(0, 12);
    return new OAuthUserInfo(
        "kakao-" + suffix,
        "kakao_" + suffix + "@mock.magampick.local",
        "kakao_" + suffix.substring(0, 6));
  }

  private String sha256Hex(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder();
      for (byte b : bytes) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new BusinessException(CommonErrorCode.INTERNAL_ERROR);
    }
  }
}
