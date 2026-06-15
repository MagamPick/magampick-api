package com.magampick.store.service;

import com.magampick.global.exception.BusinessException;
import com.magampick.store.exception.StoreErrorCode;
import java.time.LocalDate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * 사업자 진위확인 Mock — 통합테스트(@SpringBootTest) 전용. 실 국세청 호출을 격리하려 {@code test} 프로파일에서만 등록된다(런타임은 {@link
 * RealBusinessVerificationService}). FE mock 과 동일 룰 — 사업자번호 앞 3자리가 {@code "000"} 이면 거부, 그 외는 통과.
 */
@Slf4j
@Service
@Profile("test")
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
