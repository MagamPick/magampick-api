package com.magampick.global.security;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Spring Security 골격. stateless JWT, CSRF off, 인가 매트릭스 (auth.md §9). 보안 헤더(nosniff /
 * X-Frame-Options DENY / HSTS)는 Spring Security 기본값으로 충족된다.
 */
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
@EnableConfigurationProperties({JwtProperties.class, CorsProperties.class, CookieProperties.class})
public class SecurityConfig {

  private static final String[] DOCS_PATHS = {
    "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**"
  };

  /**
   * GET /api/v1/stores (목록) 만 ROLE_CUSTOMER 인증. 목록보다 먼저 매처 선언해야 첫 매치 우선이 적용됨. /stores/{id} 등 서브경로는
   * 아래 PUBLIC_GET_PATHS 의 /stores/** 로 계속 public.
   */
  private static final String CUSTOMER_STORE_LIST_PATH = "/api/v1/stores";

  private static final String[] PUBLIC_GET_PATHS = {
    "/api/v1/stores/**", "/api/v1/clearance-items/**", "/api/v1/terms/**"
  };

  private final JwtProvider jwtProvider;
  private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
  private final JwtAccessDeniedHandler jwtAccessDeniedHandler;
  private final CorsProperties corsProperties;

  @Bean
  SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    return http.csrf(AbstractHttpConfigurer::disable)
        .cors(Customizer.withDefaults())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .headers(Customizer.withDefaults())
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(DOCS_PATHS)
                    .permitAll()
                    .requestMatchers("/actuator/health")
                    .permitAll()
                    .requestMatchers("/api/v1/auth/**")
                    .permitAll()
                    // GET /api/v1/stores (목록) — ROLE_CUSTOMER 인증 필요.
                    // PUBLIC_GET_PATHS 의 /stores/** 보다 먼저 선언해야 첫 매치 우선이 적용됨.
                    .requestMatchers(HttpMethod.GET, CUSTOMER_STORE_LIST_PATH)
                    .hasRole("CUSTOMER")
                    .requestMatchers(HttpMethod.GET, PUBLIC_GET_PATHS)
                    .permitAll()
                    .requestMatchers("/api/v1/customers/me/**")
                    .hasRole("CUSTOMER")
                    .requestMatchers("/api/v1/seller/**")
                    .hasRole("SELLER")
                    .requestMatchers("/api/v1/admin/**")
                    .hasRole("ADMIN")
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            ex ->
                ex.authenticationEntryPoint(jwtAuthenticationEntryPoint)
                    .accessDeniedHandler(jwtAccessDeniedHandler))
        .addFilterBefore(
            new JwtAuthenticationFilter(jwtProvider), UsernamePasswordAuthenticationFilter.class)
        .build();
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOriginPatterns(corsProperties.allowedOrigins());
    config.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("*"));
    config.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }
}
