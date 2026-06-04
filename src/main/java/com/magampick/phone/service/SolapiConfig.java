package com.magampick.phone.service;

import net.nurigo.sdk.NurigoApp;
import net.nurigo.sdk.message.service.DefaultMessageService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 실 SOLAPI SMS({@code @Profile("!test")}) 빈 구성 — {@link SolapiProperties} 바인딩 + SOLAPI {@link
 * DefaultMessageService}. test 프로파일은 {@link MockSmsSender} 를 쓰므로 이 구성은 로딩되지 않는다 (키 없이 테스트 가능).
 */
@Configuration
@Profile("!test")
@EnableConfigurationProperties(SolapiProperties.class)
public class SolapiConfig {

  @Bean
  DefaultMessageService solapiMessageService(SolapiProperties properties) {
    return NurigoApp.INSTANCE.initialize(
        properties.apiKey(), properties.apiSecret(), properties.apiUrl());
  }
}
