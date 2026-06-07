package com.magampick.payment.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 토스 PG 설정. TOSS_SECRET_KEY 환경변수 필수 (non-test 프로파일). */
@ConfigurationProperties("toss")
public record TossProperties(String secretKey, String baseUrl) {}
