package com.magampick.store.service;

import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 국세청 진위확인 실연동(test 제외) 빈 구성 — {@link NtsBusinessProperties} 바인딩 + 호출용 {@link RestClient}. 국세청 API
 * 지연이 매장 등록 흐름을 잡지 않도록 connect 3s / read 5s 타임아웃을 둔다 (초과 시 RestClientException → {@code
 * BUSINESS_NUMBER_VERIFICATION_FAILED}). test 프로파일은 {@link MockBusinessVerificationService} 를 쓰므로
 * 로딩되지 않는다.
 */
@Configuration
@Profile("!test")
@EnableConfigurationProperties(NtsBusinessProperties.class)
public class NtsBusinessConfig {

  @Bean
  RestClient ntsRestClient(RestClient.Builder builder) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(3));
    factory.setReadTimeout(Duration.ofSeconds(5));
    return builder.requestFactory(factory).build();
  }
}
