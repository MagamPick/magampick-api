package com.magampick.store.service;

import java.time.LocalDate;

/** 국세청 사업자등록 검증. 런타임에서는 공공데이터포털 API를 호출하고, test 프로파일에서는 외부 호출을 격리한다. */
public interface BusinessVerificationService {

  /**
   * @param businessNumber 정규화된 사업자번호 (하이픈 제거 숫자 10자리)
   * @param representativeName 대표자 실명
   * @param openDate 개업일자 (사업자등록증 기재)
   * @throws com.magampick.global.exception.BusinessException 정상 영업 아님·진위확인 불일치·국세청 API 장애 등
   */
  void verify(String businessNumber, String representativeName, LocalDate openDate);
}
