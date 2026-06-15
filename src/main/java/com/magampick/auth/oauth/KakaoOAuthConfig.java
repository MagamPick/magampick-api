package com.magampick.auth.oauth;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestClient;

/**
 * 실 카카오 OAuth(dev/prod) 빈 구성 — {@link KakaoOAuthProperties} 바인딩 + 카카오 호출용 {@link RestClient}.
 * local/test 는 {@link MockKakaoOAuthProvider} 를 쓰므로 이 구성은 로딩되지 않는다.
 */
@Configuration
@Profile({"dev", "prod"})
@EnableConfigurationProperties(KakaoOAuthProperties.class)
public class KakaoOAuthConfig {

  @Bean
  RestClient kakaoRestClient(RestClient.Builder builder) {
    return builder.build();
  }
}
