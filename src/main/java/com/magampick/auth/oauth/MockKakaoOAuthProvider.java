package com.magampick.auth.oauth;

import com.magampick.auth.domain.OAuthProviderType;
import com.magampick.global.exception.BusinessException;
import com.magampick.global.exception.CommonErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** 실제 카카오 API 연동(dev/prod) 전까지 local/test 에서 쓰는 mock provider. 인가 코드 해시로 결정적 사용자 정보를 만든다. */
@Component
@Profile("!dev & !prod")
public class MockKakaoOAuthProvider implements OAuthProvider {

  @Override
  public OAuthProviderType providerType() {
    return OAuthProviderType.KAKAO;
  }

  @Override
  public OAuthUserInfo fetchUserInfo(String authorizationCode, String redirectUri) {
    String suffix = sha256Hex(authorizationCode).substring(0, 12);
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
