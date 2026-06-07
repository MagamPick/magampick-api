package com.magampick.phone.service;

import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** SMS mock 모드 런타임 토글. 시작 시 {@code app.sms.mock-enabled} 프로퍼티로 초기화되고, 관리 API 로 재시작 없이 변경 가능. */
@Slf4j
@Component
public class SmsConfig {

  private final AtomicBoolean mockEnabled;

  public SmsConfig(@Value("${app.sms.mock-enabled:false}") boolean mockEnabled) {
    this.mockEnabled = new AtomicBoolean(mockEnabled);
  }

  public boolean isMockEnabled() {
    return mockEnabled.get();
  }

  public void setMockEnabled(boolean value) {
    log.info("SMS mock 모드 변경. {}→{}", mockEnabled.get(), value);
    mockEnabled.set(value);
  }
}
