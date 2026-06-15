package com.magampick.phone.service;

import net.nurigo.sdk.NurigoApp;
import net.nurigo.sdk.message.service.DefaultMessageService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SOLAPI SMS 빈 구성. 항상 로딩 — 런타임 mock 토글({@link SmsConfig})이 양방향으로 동작하려면 빈이 항상 존재해야 한다. 자격증명이 없으면 실발송
 * 시점에 SOLAPI 에러가 발생한다 (기동 시 실패 X).
 */
@Configuration
@EnableConfigurationProperties(SolapiProperties.class)
public class SolapiConfig {

  @Bean
  DefaultMessageService solapiMessageService(SolapiProperties properties) {
    return NurigoApp.INSTANCE.initialize(
        properties.apiKey(), properties.apiSecret(), properties.apiUrl());
  }
}
