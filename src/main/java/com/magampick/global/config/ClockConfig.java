package com.magampick.global.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 애플리케이션 전역 Clock 빈. 테스트 시 Clock.fixed(...) 로 교체해 시간 결정성을 확보한다. */
@Configuration
public class ClockConfig {

  @Bean
  public Clock systemClock() {
    return Clock.system(ZoneId.of("Asia/Seoul"));
  }
}
