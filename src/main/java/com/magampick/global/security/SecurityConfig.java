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
   * GET /api/v1/stores (목록) ROLE_CUSTOMER 인증. 목록보다 먼저 매처 선언해야 첫 매치 우선 적용됨.
   *
   * <p>매처 순서:
   *
   * <ol>
   *   <li>{@code /api/v1/stores} — 전체 목록, ROLE_CUSTOMER
   *   <li>{@code /api/v1/stores/*} — 단건 상세(단일 세그먼트), ROLE_CUSTOMER
   *   <li>{@code /api/v1/clearance-items/*} — 떨이 상세(단일 세그먼트), ROLE_CUSTOMER
   *   <li>{@code /api/v1/products/*} — 일반 상품 상세(단일 세그먼트), ROLE_CUSTOMER
   *   <li>{@code /api/v1/stores/**} — 서브경로(clearance-items·menu·reviews 등), public
   *   <li>{@code /api/v1/clearance-items/**} — 루트 외 서브경로, public
   * </ol>
   */
  private static final String CUSTOMER_STORE_LIST_PATH = "/api/v1/stores";

  private static final String CUSTOMER_STORE_DETAIL_PATH = "/api/v1/stores/*";

  /** GET /api/v1/clearance-items/{id} — 단일 세그먼트, ROLE_CUSTOMER (인가 auth.md §9). */
  private static final String CUSTOMER_DEAL_DETAIL_PATH = "/api/v1/clearance-items/*";

  /** GET /api/v1/products/{id} — 단일 세그먼트, ROLE_CUSTOMER (인가 auth.md §9). */
  private static final String CUSTOMER_PRODUCT_DETAIL_PATH = "/api/v1/products/*";

  /** GET /api/v1/search, /api/v1/search/** — 검색·자동완성, ROLE_CUSTOMER (Phase 9). */
  private static final String[] CUSTOMER_SEARCH_PATHS = {"/api/v1/search", "/api/v1/search/**"};

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
                    // DEV 전용 시드 엔드포인트 — local/dev 프로파일에서만 컨트롤러 활성화되므로 prod 에선 404
                    .requestMatchers("/api/v1/dev/test/**")
                    .permitAll()
                    // GET /api/v1/stores (목록) — ROLE_CUSTOMER.
                    // GET /api/v1/stores/* (단건 상세) — ROLE_CUSTOMER.
                    // GET /api/v1/clearance-items/* (떨이 상세, 단일 세그먼트) — ROLE_CUSTOMER.
                    // GET /api/v1/products/* (일반 상품 상세, 단일 세그먼트) — ROLE_CUSTOMER.
                    // 네 매처 모두 PUBLIC_GET_PATHS(/stores/**, /clearance-items/**) 보다 먼저 선언해야 첫 매치 우선
                    // 적용.
                    .requestMatchers(HttpMethod.GET, CUSTOMER_STORE_LIST_PATH)
                    .hasRole("CUSTOMER")
                    .requestMatchers(HttpMethod.GET, CUSTOMER_STORE_DETAIL_PATH)
                    .hasRole("CUSTOMER")
                    .requestMatchers(HttpMethod.GET, CUSTOMER_DEAL_DETAIL_PATH)
                    .hasRole("CUSTOMER")
                    .requestMatchers(HttpMethod.GET, CUSTOMER_PRODUCT_DETAIL_PATH)
                    .hasRole("CUSTOMER")
                    // GET /api/v1/search (검색) / /api/v1/search/** (자동완성 등) — ROLE_CUSTOMER (Phase
                    // 9)
                    .requestMatchers(HttpMethod.GET, CUSTOMER_SEARCH_PATHS)
                    .hasRole("CUSTOMER")
                    .requestMatchers(HttpMethod.GET, PUBLIC_GET_PATHS)
                    .permitAll()
                    .requestMatchers("/api/v1/customers/me/**")
                    .hasRole("CUSTOMER")
                    // POST /api/v1/payments/toss/confirm — 토스 결제 확인 (소비자)
                    .requestMatchers(HttpMethod.POST, "/api/v1/payments/toss/confirm")
                    .hasRole("CUSTOMER")
                    // POST /api/v1/orders, GET /api/v1/orders, GET /api/v1/orders/* — 소비자 전용
                    // POST /api/v1/orders/{id}/cancel — 소비자 취소 (auth.md §9)
                    // POST /api/v1/orders/{id}/refund — 소비자 환불 요청
                    .requestMatchers(HttpMethod.POST, "/api/v1/orders")
                    .hasRole("CUSTOMER")
                    .requestMatchers(HttpMethod.POST, "/api/v1/orders/*/cancel")
                    .hasRole("CUSTOMER")
                    .requestMatchers(HttpMethod.POST, "/api/v1/orders/*/refund")
                    .hasRole("CUSTOMER")
                    .requestMatchers(HttpMethod.GET, "/api/v1/orders")
                    .hasRole("CUSTOMER")
                    .requestMatchers(HttpMethod.GET, "/api/v1/orders/*")
                    .hasRole("CUSTOMER")
                    // 리뷰 write — ROLE_CUSTOMER 전용
                    .requestMatchers(HttpMethod.GET, "/api/v1/orders/*/review")
                    .hasRole("CUSTOMER")
                    .requestMatchers(HttpMethod.POST, "/api/v1/orders/*/reviews")
                    .hasRole("CUSTOMER")
                    .requestMatchers(HttpMethod.PUT, "/api/v1/reviews/*")
                    .hasRole("CUSTOMER")
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/reviews/*")
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
