package com.magampick.store.service;

import com.magampick.global.exception.BusinessException;
import com.magampick.store.exception.StoreErrorCode;
import java.time.LocalDate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * 사업자 진위확인 Mock. 공공데이터포털 국세청 API 실연동 전까지 사용한다. FE mock 과 동일 룰 — 사업자번호 앞 3자리가 {@code "000"} 이면 진위확인
 * 불일치로 거부, 그 외는 통과. 실연동 시 (번호·대표자명·개업일자) 세 값 일치 여부를 외부 호출로 판정.
 */
@Slf4j
@Service
@Profile("!prod")
public class MockBusinessVerificationService implements BusinessVerificationService {

  @Override
  public void verify(String businessNumber, String representativeName, LocalDate openDate) {
    if (businessNumber.startsWith("000")) {
      throw new BusinessException(StoreErrorCode.BUSINESS_INFO_MISMATCH);
    }
    log.info(
        "사업자 진위확인 mock 통과. businessNumber={}, representativeName={}, openDate={}",
        businessNumber,
        representativeName,
        openDate);
  }
}
