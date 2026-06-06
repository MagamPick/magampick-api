package com.magampick.auth.oauth;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.magampick.auth.domain.OAuthProviderType;
import com.magampick.auth.exception.AuthErrorCode;
import com.magampick.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 실 카카오 OAuth provider (test 외). 인가 코드(B안) → 토큰 교환(kauth) → 사용자 정보 조회(kapi). 외부 호출 실패는 {@code
 * SOCIAL_AUTH_FAILED}, 이메일 미동의는 {@code KAKAO_EMAIL_REQUIRED} 로 매핑한다.
 */
@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class RealKakaoOAuthProvider implements OAuthProvider {

  private final KakaoOAuthProperties properties;
  private final RestClient kakaoRestClient;

  @Override
  public OAuthProviderType providerType() {
    return OAuthProviderType.KAKAO;
  }

  @Override
  public OAuthUserInfo fetchUserInfo(String authorizationCode, String redirectUri) {
    String accessToken = exchangeToken(authorizationCode, redirectUri);
    return fetchUser(accessToken);
  }

  /** 인가 코드 + client_secret 으로 카카오 access token 을 교환한다. redirect_uri 는 인가 때 값과 동일해야 한다. */
  private String exchangeToken(String authorizationCode, String redirectUri) {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "authorization_code");
    form.add("client_id", properties.clientId());
    form.add("client_secret", properties.clientSecret());
    form.add("redirect_uri", redirectUri);
    form.add("code", authorizationCode);
    try {
      KakaoTokenResponse response =
          kakaoRestClient
              .post()
              .uri(properties.tokenUri())
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .body(form)
              .retrieve()
              .body(KakaoTokenResponse.class);
      if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
        throw new BusinessException(AuthErrorCode.SOCIAL_AUTH_FAILED);
      }
      return response.accessToken();
    } catch (RestClientException e) {
      log.warn("카카오 토큰 교환 실패", e);
      throw new BusinessException(AuthErrorCode.SOCIAL_AUTH_FAILED);
    }
  }

  /** access token 으로 사용자 정보를 조회한다. 이메일 미동의(없음)는 KAKAO_EMAIL_REQUIRED. */
  private OAuthUserInfo fetchUser(String accessToken) {
    KakaoUserResponse response;
    try {
      response =
          kakaoRestClient
              .get()
              .uri(properties.userInfoUri())
              .header("Authorization", "Bearer " + accessToken)
              .retrieve()
              .body(KakaoUserResponse.class);
    } catch (RestClientException e) {
      log.warn("카카오 사용자 조회 실패", e);
      throw new BusinessException(AuthErrorCode.SOCIAL_AUTH_FAILED);
    }
    if (response == null || response.id() == null) {
      throw new BusinessException(AuthErrorCode.SOCIAL_AUTH_FAILED);
    }
    String email = response.email();
    if (email == null || email.isBlank()) {
      throw new BusinessException(AuthErrorCode.KAKAO_EMAIL_REQUIRED);
    }
    return new OAuthUserInfo(String.valueOf(response.id()), email, response.nickname());
  }

  private record KakaoTokenResponse(@JsonProperty("access_token") String accessToken) {}

  private record KakaoUserResponse(
      Long id, @JsonProperty("kakao_account") KakaoAccount kakaoAccount) {

    String email() {
      return kakaoAccount == null ? null : kakaoAccount.email();
    }

    String nickname() {
      if (kakaoAccount == null || kakaoAccount.profile() == null) {
        return null;
      }
      return kakaoAccount.profile().nickname();
    }

    private record KakaoAccount(String email, Profile profile) {
      private record Profile(String nickname) {}
    }
  }
}
