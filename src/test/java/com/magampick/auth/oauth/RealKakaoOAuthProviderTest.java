package com.magampick.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.magampick.auth.exception.AuthErrorCode;
import com.magampick.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RealKakaoOAuthProviderTest {

  private static final String TOKEN_URI = "https://kauth.kakao.com/oauth/token";
  private static final String USER_URI = "https://kapi.kakao.com/v2/user/me";
  private static final KakaoOAuthProperties PROPS =
      new KakaoOAuthProperties("client-id", "client-secret", TOKEN_URI, USER_URI);

  private MockRestServiceServer server;

  private RealKakaoOAuthProvider providerWith(MockRestServiceServer[] holder) {
    RestClient.Builder builder = RestClient.builder();
    holder[0] = MockRestServiceServer.bindTo(builder).build();
    return new RealKakaoOAuthProvider(PROPS, builder.build());
  }

  @Test
  void 인가코드_교환_후_사용자정보_반환() {
    // given
    MockRestServiceServer[] holder = new MockRestServiceServer[1];
    RealKakaoOAuthProvider provider = providerWith(holder);
    server = holder[0];
    server
        .expect(requestTo(TOKEN_URI))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess("{\"access_token\":\"at\"}", MediaType.APPLICATION_JSON));
    server
        .expect(requestTo(USER_URI))
        .andExpect(method(HttpMethod.GET))
        .andRespond(
            withSuccess(
                "{\"id\":12345,\"kakao_account\":{\"email\":\"u@kakao.com\",\"profile\":{\"nickname\":\"카카오유저\"}}}",
                MediaType.APPLICATION_JSON));

    // when
    OAuthUserInfo info = provider.fetchUserInfo("auth-code", "https://app.example/cb");

    // then
    assertThat(info.providerUserId()).isEqualTo("12345");
    assertThat(info.email()).isEqualTo("u@kakao.com");
    assertThat(info.nickname()).isEqualTo("카카오유저");
    server.verify();
  }

  @Test
  void 이메일_동의_없으면_KAKAO_EMAIL_REQUIRED() {
    // given — user/me 응답에 email 없음
    MockRestServiceServer[] holder = new MockRestServiceServer[1];
    RealKakaoOAuthProvider provider = providerWith(holder);
    server = holder[0];
    server
        .expect(requestTo(TOKEN_URI))
        .andRespond(withSuccess("{\"access_token\":\"at\"}", MediaType.APPLICATION_JSON));
    server
        .expect(requestTo(USER_URI))
        .andRespond(
            withSuccess(
                "{\"id\":12345,\"kakao_account\":{\"profile\":{\"nickname\":\"카카오유저\"}}}",
                MediaType.APPLICATION_JSON));

    // when & then
    assertThatThrownBy(() -> provider.fetchUserInfo("auth-code", "https://app.example/cb"))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.KAKAO_EMAIL_REQUIRED);
  }

  @Test
  void 토큰교환_실패시_SOCIAL_AUTH_FAILED() {
    // given — 토큰 엔드포인트가 401
    MockRestServiceServer[] holder = new MockRestServiceServer[1];
    RealKakaoOAuthProvider provider = providerWith(holder);
    server = holder[0];
    server.expect(requestTo(TOKEN_URI)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

    // when & then
    assertThatThrownBy(() -> provider.fetchUserInfo("bad-code", "https://app.example/cb"))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.SOCIAL_AUTH_FAILED);
  }
}
