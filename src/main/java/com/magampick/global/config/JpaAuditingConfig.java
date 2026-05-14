package com.magampick.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA Auditing 활성화. Application 이 아닌 별도 config 에 두어 @WebMvcTest 등 슬라이스 테스트가 auditing 빈을 요구하지 않게 한다.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {}
