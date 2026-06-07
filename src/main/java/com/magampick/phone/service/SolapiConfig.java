package com.magampick.phone.service;

import net.nurigo.sdk.NurigoApp;
import net.nurigo.sdk.message.service.DefaultMessageService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SOLAPI SMS 빈 구성. {@code app.sms.mock-enabled=false}(기본값) 일 때만 로딩 — mock 모드에서는 {@link
 * SolapiProperties} 검증을 건너뛰므로 키 없이 기동 가능.
 */
@Configuration
@ConditionalOnProperty(name = "app.sms.mock-enabled", havingValue = "false", matchIfMissing = true)
@EnableConfigurationProperties(SolapiProperties.class)
public class SolapiConfig {

  @Bean
  DefaultMessageService solapiMessageService(SolapiProperties properties) {
    return NurigoApp.INSTANCE.initialize(
        properties.apiKey(), properties.apiSecret(), properties.apiUrl());
  }
}
