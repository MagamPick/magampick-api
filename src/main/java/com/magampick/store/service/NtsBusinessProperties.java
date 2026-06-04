package com.magampick.store.service;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 국세청 사업자등록 진위확인 API 설정. application.yaml 의 {@code nts.*} 에 바인딩한다. {@code service-key} 는
 * 환경변수({@code NTS_BUSINESS_API_KEY}) — 비어 있으면 호출 시 국세청이 거부해 {@code
 * BUSINESS_NUMBER_VERIFICATION_FAILED} 로 떨어진다(부팅은 가능). {@code !test} 프로필({@link
 * NtsBusinessConfig})에서만 등록된다.
 */
@Validated
@ConfigurationProperties(prefix = "nts")
public record NtsBusinessProperties(String serviceKey, @NotBlank String validateUrl) {}
