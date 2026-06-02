package com.magampick.store.service;

import java.time.LocalDate;

/**
 * 국세청 사업자 진위확인. 사업자번호·대표자명·개업일자 세 값이 일치할 때만 통과한다 (단순 상태조회 X). 노션 "매장 등록 신청" 정책.
 *
 * <p>실연동(공공데이터포털)은 후속 — MVP 는 {@link MockBusinessVerificationService} 가 stub.
 */
public interface BusinessVerificationService {

  /**
   * @param businessNumber 정규화된 사업자번호 (하이픈 제거 숫자 10자리)
   * @param representativeName 대표자 실명
   * @param openDate 개업일자 (사업자등록증 기재)
   * @throws com.magampick.global.exception.BusinessException 진위확인 실패·국세청 API 장애 등
   */
  void verify(String businessNumber, String representativeName, LocalDate openDate);
}
