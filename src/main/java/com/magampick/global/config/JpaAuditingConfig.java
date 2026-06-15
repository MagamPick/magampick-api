package com.magampick.global.config;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA Auditing 활성화. Application 이 아닌 별도 config 에 두어 @WebMvcTest 등 슬라이스 테스트가 auditing 빈을 요구하지 않게 한다.
 * DateTimeProvider 를 KST 고정해 @CreatedDate/@LastModifiedDate 가 항상 KST 시각을 기록하도록 한다. (서버 JVM 기본 시간대가
 * UTC 인 환경에서 TODAY 날짜 필터 오작동 방지)
 */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "kstDateTimeProvider")
public class JpaAuditingConfig {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  @Bean
  public DateTimeProvider kstDateTimeProvider() {
    return () -> Optional.of(LocalDateTime.now(KST));
  }
}
