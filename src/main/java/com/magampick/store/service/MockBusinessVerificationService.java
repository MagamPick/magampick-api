package com.magampick.store.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Profile("!prod")
public class MockBusinessVerificationService implements BusinessVerificationService {

  @Override
  public void verify(String businessNumber) {
    log.info("사업자 인증 mock 통과. businessNumber={}", businessNumber);
  }
}
