package com.magampick.global.security;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** CORS 허용 origin. 프로필별로 application-{env}.yaml 의 cors.allowed-origins 에 정의 (auth.md §10). */
@Validated
@ConfigurationProperties(prefix = "cors")
public record CorsProperties(@NotEmpty List<String> allowedOrigins) {}
