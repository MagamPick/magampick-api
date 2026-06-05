package com.magampick.store.service;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 국세청 사업자등록 검증 API 설정. application.yaml 의 {@code nts.*} 에 바인딩. {@code service-key} 는 환경변수({@code
 * NTS_BUSINESS_API_KEY}) — 비어 있으면 호출 시 국세청이 거부해 {@code BUSINESS_NUMBER_VERIFICATION_FAILED}(부팅은
 * 가능).
 *
 * <p>{@code verification-mode} 로 검증 방식을 고른다 — {@code status}(기본, 상태조회: 번호만; 개업일자·대표자명 없이 출시 전 데모
 * 단계) / {@code validate}(진위확인: 번호+대표자명+개업일자 3요소). 입력 시그니처는 동일하고 status 모드에선 번호만 국세청에 보낸다. {@code
 * !test} 프로필({@link NtsBusinessConfig})에서만 등록된다.
 */
@Validated
@ConfigurationProperties(prefix = "nts")
public record NtsBusinessProperties(
    String serviceKey,
    String verificationMode,
    @NotBlank String validateUrl,
    @NotBlank String statusUrl) {

  /** {@code status} 모드(상태조회, 번호만) 여부. 미설정/그 외 값은 {@code validate}(진위확인)로 동작. */
  public boolean isStatusMode() {
    return "status".equalsIgnoreCase(verificationMode);
  }
}
