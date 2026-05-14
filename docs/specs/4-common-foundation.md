# Spec: 공통 기반 코드

> 이슈: #4

## 1. Context

도메인 코드(매장 / 주문 / 상품 ...)를 작성하기 전에 필요한 비-도메인 공통 기반(`global/` 패키지)과 빌드 설정을 한 번에 깔아둔다. 내용은 대부분 `coding-convention.md` · `api-convention.md` 에 이미 규정되어 있으며, 이 이슈는 그 규정을 코드로 옮기는 작업이다. 모든 후속 도메인 이슈(`users → stores → ...`)가 이 기반 위에서 작업하므로 머지 순서상 가장 먼저 진행한다.

## 2. Scope

### In Scope
- `build.gradle` 의존성 추가 — coding-convention §8 의 7종 중 **미추가분(MapStruct, springdoc-openapi, JWT, Firebase Admin SDK)**. Flyway·hibernate-spatial 은 이미 존재
- `global/common/` — `BaseEntity`
- `global/config/` — `JpaAuditingConfig`, `SwaggerConfig`
- `global/exception/` — `BaseErrorCode`, `CommonErrorCode`, `BusinessException`, `ErrorResponse`, `GlobalExceptionHandler`
- `global/response/` — `ApiResponse<T>`, `ApiResponseAdvice`, `PageResponse<T>`, `SliceResponse<T>`
- `global/security/` — `SecurityConfig` 골격, `JwtProvider`(+`JwtProperties`), `JwtAuthenticationFilter`, `CustomUserDetails`, `JwtAuthenticationEntryPoint`, `exception/AuthErrorCode`
- `application*.yaml` — springdoc prod 차단, JWT 프로퍼티, CORS allowed-origins 프로필별

### Out of Scope
- 로그인 / 회원가입 / 토큰 발급·재발급 **API** — 회원·인증 도메인 이슈
- 도메인 Entity / 테이블 / 마이그레이션(V2~) — 각 도메인 이슈
- `refresh_tokens` 테이블 및 refresh 영속화 — 회원·인증 도메인 이슈
- `FirebaseApp` 초기화 및 FCM 발송 로직 — 알림 도메인 이슈
- 전역 코딩 컨벤션 문서 수정 — 별도 이슈

## 3. User Roles

해당 없음 — 비-도메인 인프라 작업. (단 `global/security/` 가 `ROLE_CUSTOMER` / `ROLE_SELLER` / `ROLE_ADMIN` 인가 골격을 깔며, 실제 사용자는 후속 도메인에서.)

## 4. API Specification

이 이슈는 **REST 엔드포인트를 추가하지 않는다.** 다만 인프라 엔드포인트가 노출되며 `SecurityConfig` permitAll 대상이 된다:

| 엔드포인트 | 용도 | 노출 환경 |
|---|---|---|
| `GET /swagger-ui/index.html`, `/v3/api-docs/**` | API 문서 | local / dev (prod 차단) |
| `GET /actuator/health` | 헬스체크 | 전 환경 |

cross-cutting 동작(자동 envelope wrap, 예외 → 에러 응답)은 §6 참조.

## 5. Data Model

### 새 테이블
없음.

### 기존 테이블 변경
없음.

### 마이그레이션
없음 (V2~ 도메인 테이블은 각 도메인 이슈). `BaseEntity` 는 `@MappedSuperclass` 로, 상속하는 미래 테이블에 `created_at TIMESTAMP NOT NULL` / `updated_at TIMESTAMP NOT NULL` 컬럼을 기여한다 — 컬럼 정의는 각 도메인 마이그레이션에 포함.

### ERD
- `docs/erd/tables/` — 작성 대상 없음 (테이블 없음)
- `docs/erd/overview.md` — 갱신 없음

## 6. Business Logic

### 6-1. 응답 envelope 자동 wrap
- `ApiResponseAdvice` (`ResponseBodyAdvice`) 가 모든 컨트롤러 반환값을 `ApiResponse.success(body)` 로 감싼다
- 이미 `ApiResponse` 면 그대로 통과 (에러 응답 이중 wrap 방지)
- `@JsonInclude(NON_NULL)` 로 `data`/`error` 중 null 필드 omit

### 6-2. 예외 처리 흐름
| 예외 | 핸들러 처리 | HTTP |
|---|---|---|
| `BusinessException` | `errorCode.getStatus()` + `ErrorResponse.from(errorCode)` | errorCode 별 |
| `MethodArgumentNotValidException` | `CommonErrorCode.INVALID_INPUT` + `fieldErrors` | 400 |
| 그 외 `Exception` | `CommonErrorCode.INTERNAL_ERROR`, `log.error` | 500 |

- 모든 에러 응답은 `ApiResponse.error(ErrorResponse)` envelope
- 도메인별 `ErrorCode` enum 은 `BaseErrorCode` 구현 → `BusinessException` 하나로 throw

### 6-3. JWT 인증 필터 흐름
1. `JwtAuthenticationFilter`(`OncePerRequestFilter`) 가 `Authorization: Bearer {token}` 추출
2. 헤더 없음 → 그냥 다음 필터로 (인증 안 된 상태, permitAll 경로면 통과 / 아니면 `JwtAuthenticationEntryPoint` 가 401)
3. 토큰 있음 → `JwtProvider.parse()` 로 검증
   - 유효 → claims(`sub`, `role`)로 `CustomUserDetails` 생성 → `UsernamePasswordAuthenticationToken` 을 `SecurityContext` 에 set
   - 만료 → `AuthErrorCode.TOKEN_EXPIRED`
   - 서명 무효 / 파싱 실패 → `AuthErrorCode.INVALID_TOKEN`
4. 인증 실패 시 `JwtAuthenticationEntryPoint` 가 `ApiResponse.error` JSON(401) 직접 write

### 6-4. JWT 발급 (유틸만, 호출부는 Out of Scope)
- `JwtProvider.issueAccessToken(userId, role)` — HS256, 만료 `jwt.access-token-validity`(기본 30분), claims `sub`/`role`/`iss`
- `JwtProvider.issueRefreshToken(userId, role)` — HS256, 만료 `jwt.refresh-token-validity`(기본 14일)
- refresh 토큰 **DB 영속화는 회원·인증 이슈** — 여기선 토큰 문자열 생성까지만

### Validation Rules
- `jwt.secret` — 256bit(32자) 이상. 미설정 시 앱 기동 실패 (`@Validated` + `@NotBlank`)

### Error Cases
| 상황 | 에러 코드 | HTTP |
|---|---|---|
| 토큰 없음 (인증 필요 경로) | `CommonErrorCode.UNAUTHORIZED` | 401 |
| 토큰 만료 | `AuthErrorCode.TOKEN_EXPIRED` | 401 |
| 토큰 서명 무효 / 파싱 실패 | `AuthErrorCode.INVALID_TOKEN` | 401 |
| 권한 부족 | `CommonErrorCode.FORBIDDEN` | 403 |

### Edge Cases
- **이중 wrap**: 에러 응답(`ApiResponse`)이 `ApiResponseAdvice` 를 또 거치는 경우 → `instanceof ApiResponse` 체크로 skip
- **`@WebMvcTest` 슬라이스**: `JpaAuditingConfig` 가 별도 클래스라 `@WebMvcTest` 에 로드되지 않음 (auditing 이 web 슬라이스 테스트를 깨지 않게)
- **204 No Content**: body 가 없으면 `ApiResponseAdvice` 가 wrap 하지 않음 (`beforeBodyWrite` body=null 통과)
- **springdoc / actuator 경로**: `ApiResponseAdvice` 가 `/v3/api-docs`, `/swagger-ui` 응답까지 wrap 하지 않도록 `@RestControllerAdvice(basePackages = "com.magampick")` 로 컨트롤러 범위 한정

### Test Cases

#### Service 단위 테스트 (`JwtProvider`)
- `액세스_토큰_발급_후_파싱_성공`
- `토큰_파싱_userId_role_claim_일치`
- `만료된_토큰_파싱_시_예외`
- `잘못된_서명_토큰_파싱_시_예외`

#### Controller `@WebMvcTest` (테스트용 더미 컨트롤러로 cross-cutting 검증)
- `정상_응답_success_envelope_로_wrap`
- `BusinessException_발생_시_에러_envelope_와_상태코드`
- `검증_실패_시_400_INVALID_INPUT_과_fieldErrors`
- `미처리_예외_시_500_INTERNAL_ERROR`

#### 통합 테스트 (`@SpringBootTest`, 핵심 흐름)
- `토큰_없이_인증필요_경로_접근_시_401`
- `유효한_토큰으로_인증필요_경로_접근_성공`

## 7. External Dependencies

`build.gradle` 추가분 (이미 있는 Flyway·hibernate-spatial 제외):

```gradle
// MapStruct
implementation 'org.mapstruct:mapstruct:1.6.3'
annotationProcessor 'org.mapstruct:mapstruct-processor:1.6.3'
annotationProcessor 'org.projectlombok:lombok-mapstruct-binding:0.2.0'

// springdoc-openapi (Swagger UI) — Spring Boot 3.5 호환 버전
implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.6'

// JWT
implementation 'io.jsonwebtoken:jjwt-api:0.12.6'
runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.12.6'
runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.12.6'

// Firebase Admin SDK (의존성만 — 초기화는 알림 도메인)
implementation 'com.google.firebase:firebase-admin:9.4.1'
```

- **JWT**: `JWT_SECRET` / `JWT_ACCESS_TTL_MINUTES`(기본 30) / `JWT_REFRESH_TTL_DAYS`(기본 14) 환경변수 → `application.yaml` 의 `jwt.*` 프로퍼티로 바인딩 (auth.md §12)
- **Firebase**: 이 이슈에선 의존성 선언만. `FirebaseApp.initializeApp(...)` 및 credential 환경변수는 알림 도메인 이슈
- **MapStruct**: 의존성만. Mapper 인터페이스는 각 도메인에서

## 8. Implementation Notes

### 패키지 / 클래스 구조
```
com.magampick
├── MagampickApiApplication.java        # 변경 없음 (auditing 은 별도 config)
└── global/
    ├── common/
    │   └── BaseEntity.java             # @MappedSuperclass, @EntityListeners(AuditingEntityListener)
    ├── config/
    │   ├── JpaAuditingConfig.java      # @Configuration @EnableJpaAuditing
    │   └── SwaggerConfig.java          # OpenAPI + 역할별 3 GroupedOpenApi (api-convention §12)
    ├── exception/
    │   ├── BaseErrorCode.java          # interface: getStatus/getCode/getMessage
    │   ├── CommonErrorCode.java        # INVALID_INPUT, UNAUTHORIZED, FORBIDDEN, INTERNAL_ERROR
    │   ├── BusinessException.java      # RuntimeException + BaseErrorCode
    │   ├── ErrorResponse.java          # record (code, message, timestamp, fieldErrors) + FieldError
    │   └── GlobalExceptionHandler.java # @RestControllerAdvice
    ├── response/
    │   ├── ApiResponse.java            # record<T>(success, data, error)
    │   ├── ApiResponseAdvice.java      # @RestControllerAdvice, ResponseBodyAdvice
    │   ├── PageResponse.java           # record<T>, of(Page)
    │   └── SliceResponse.java          # record<T>, of(Slice)
    └── security/
        ├── SecurityConfig.java         # @EnableMethodSecurity, SecurityFilterChain, PasswordEncoder, CorsConfigurationSource
        ├── JwtProvider.java            # 발급 + 검증/파싱
        ├── JwtProperties.java          # @ConfigurationProperties("jwt"), record, @Validated
        ├── JwtAuthenticationFilter.java# OncePerRequestFilter
        ├── CustomUserDetails.java      # implements UserDetails (userId, role)
        ├── JwtAuthenticationEntryPoint.java # implements AuthenticationEntryPoint
        ├── Role.java                   # enum CUSTOMER/SELLER/ADMIN
        └── exception/
            └── AuthErrorCode.java      # implements BaseErrorCode
```

### 구현 결정
- **`JpaAuditingConfig` 별도 클래스**: `@EnableJpaAuditing` 을 Application 이 아닌 별도 `@Configuration` 에 둠 → `@WebMvcTest` 등 슬라이스 테스트가 auditing 빈을 요구하지 않음
- **`AuthErrorCode` 범위**: 이 이슈가 **실제 사용하는 토큰 검증 코드만** 정의 (`TOKEN_EXPIRED`, `INVALID_TOKEN`). auth.md §13 의 로그인/가입 코드(`INVALID_CREDENTIALS`, `EMAIL_ALREADY_EXISTS`, `PHONE_NOT_VERIFIED`, `SELLER_NOT_APPROVED`)는 해당 로직이 Out of Scope 이므로 **회원·인증 도메인 이슈에서 enum 에 추가**. `UNAUTHORIZED`/`FORBIDDEN` 은 `CommonErrorCode` 사용
- **`CustomUserDetails`**: `UserDetails` 구현, `userId`(Long) + `role`(enum) 보유. stateless 라 `getPassword()` 는 빈 문자열, `UserDetailsService`(DB 조회) 없음 — 필터가 검증된 JWT claim 만 신뢰. `@AuthenticationPrincipal CustomUserDetails` 로 컨트롤러에서 직접 주입 (별도 `PrincipalResolver` 불필요 — auth.md §14 의 `PrincipalResolver` 는 도입 시 회원·인증 이슈에서 재검토)
- **`Role` enum 위치**: `global/security/Role.java` (`CUSTOMER`/`SELLER`/`ADMIN`) — `getAuthority()` 가 `ROLE_` prefix 부여. 사용자 테이블은 후속 도메인이지만 role 식별자는 security 기반에 필요
- **인증 실패 응답**: `JwtAuthenticationFilter` 는 검증 실패 시 예외를 request attribute 에 담아 `JwtAuthenticationEntryPoint` 로 위임, EntryPoint 가 `ApiResponse.error` JSON 직접 write (Spring Security 필터 체인은 `@RestControllerAdvice` 범위 밖이라 `GlobalExceptionHandler` 가 못 잡음)
- **CORS**: `cors.allowed-origins` 프로퍼티(프로필별)를 `CorsConfigurationSource` 빈이 읽음 — local `*`, dev/prod 는 auth.md §10 도메인. 코드 하드코딩 X
- **보안 헤더**: `SecurityConfig` 에서 `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY` 전 환경. HSTS 는 prod 만 (auth.md §10) — 프로필 분기 또는 `application-prod.yaml` 차원
- **`ApiResponseAdvice` 적용 범위**: `@RestControllerAdvice(basePackages = "com.magampick")` 로 한정 → springdoc(`/v3/api-docs`)·actuator 응답은 wrap 대상에서 제외
- **트랜잭션 / 동시성 / 비동기 / 캐시**: 해당 없음 (인프라 골격, 비즈니스 로직 없음)
- **MagampickApiApplication 변경**: 없음. coding-convention §8 예시는 `@EnableJpaAuditing` 을 Application 에 달지만, 위 결정대로 별도 config 채택
