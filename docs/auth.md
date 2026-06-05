# Authentication & Authorization

JWT 기반 인증·인가 정책. API 레벨 요약은 [`api-convention.md` §9](api-convention.md) 참조.

> 정책 / 결정 사항만 기록. 구현은 `global/security/` 패키지.

---

## 1. 사용자 종류

| 종류 | 테이블 | Role | Prefix |
|---|---|---|---|
| 소비자 | `customers` | `ROLE_CUSTOMER` | (없음, 기본) |
| 사장 | `sellers` | `ROLE_SELLER` | `/seller` |
| 관리자 | `admins` | `ROLE_ADMIN` | `/admin` |

세 테이블 분리 — 가입 흐름·필드 다름.

JWT `sub` 는 **사용자 ID 만** (예: `"42"`). 어느 테이블인지는 별도 `role` claim 으로 판별 (§4 참조).

---

## 2. 회원가입

### 소비자

```
1. 휴대폰 본인인증 (외부 API) → verification_token 발급
2. POST /api/v1/auth/signup
   { email, password, nickname, verificationToken }
3. 서버: verificationToken 검증 → customers row 생성 (phone_verified_at 기록)
4. 자동 로그인 → access + refresh 발급
```

### 사장

```
1. 휴대폰 본인인증
2. POST /api/v1/auth/seller/signup
   multipart/form-data
   - request: { email, password, ownerName, phone, verificationToken, agreedTermIds, store }
   - image: 첫 매장 대표 이미지
3. 서버: 사업자 진위확인 + 지오코딩 + 이미지 업로드를 먼저 수행
4. 서버: sellers row + seller_terms_agreements + stores row + 토큰 발급을 한 트랜잭션으로 처리
```

> **사장 가입 경로 A**: 가입 완료 = 첫 매장 1개 보유 상태. `sellers.business_number` 는 첫 매장 사업자번호를 정규화한 값으로 복사한다. 매장 등록 실패 또는 약관 동의 실패 시 가입 전체가 롤백되어 매장 0개 사장이 남지 않는다.

### 관리자

- **셀프 가입 불가**. 기존 관리자가 초대하는 방식
- 초대 구현 방식 미정 (Pending)

---

## 3. 로그인

### 이메일 + 비밀번호

```
POST /api/v1/auth/login
{ "email": "...", "password": "..." }
```

응답:
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJ...",
    "refreshToken": "eyJ...",
    "accessExpiresIn": 1800
  }
}
```

### 카카오 OAuth (소셜 로그인)

```
POST /api/v1/auth/kakao
{ "authorizationCode": "...", "redirectUri": "..." }
```

흐름:
1. 클라이언트가 카카오 인가 → **authorization code** (등록된 redirect URI 로 받아 서버에 전송 — B안)
2. 서버: code + `client_secret` 으로 토큰 교환(`kauth.kakao.com`) → access token → 사용자 정보 조회(`kapi.kakao.com`): email + provider_user_id + nickname. 교환/조회 실패 시 `SOCIAL_AUTH_FAILED` (502), 이메일 미동의 시 `KAKAO_EMAIL_REQUIRED` (400)
3. **매칭 키 `(provider, provider_user_id)`** 로 `customer_oauth_accounts` 조회 후 분기:
   - **기존 연결** → `{ "status": "EXISTING" }` + access(바디) + refresh(쿠키) 즉시 로그인
   - **미존재 (신규)** → `customers.email` 충돌 검사 (충돌 시 `EMAIL_ALREADY_REGISTERED` 409 — 자동 연결 안 함) 후 **소셜 토큰**(Redis, 15분 1회용) 발급. 가입은 보류하고 `{ "status": "NEW", "socialToken", "email", "nickname" }` 반환
4. **신규 추가정보 가입** (`POST /api/v1/auth/signup/social` — `socialToken` + 약관·본인인증·주소·닉네임, 일반 가입과 동일): 소셜 토큰으로 카카오 정보 복원 → 이메일 충돌 재검사 → `customer`(`password_hash = NULL`) + `customer_oauth_accounts` + 약관 동의 + 기본 주소 = **한 트랜잭션** → JWT 발급 (201). 소셜 토큰 만료/무효 시 `SOCIAL_TOKEN_INVALID` (400)

**소셜 로그인은 소비자만**. 사장 / 관리자는 이메일 + 비밀번호.

**계정 모델 — 별개 (Separate Accounts)**:

- 소셜 가입자와 이메일 가입자는 **같은 사람이라도 별개 `customers` row** 를 가진다. 자동 linking 안 함 (한국 이커머스 표준 패턴)
- 한 customer 는 인증 수단 한 가지만 보유 — 이메일+비번 **또는** 카카오 OAuth (DB 차원에서 `customer_oauth_accounts.customer_id` UNIQUE 로 강제)
- 카카오로 가입한 customer 의 `password_hash` 는 `NULL` → 이메일+비번 로그인 시 비밀번호 불일치 처리
- 이메일+비번 가입자가 카카오로도 가입 시도 시:
  - **같은 이메일** → `EMAIL_ALREADY_REGISTERED` 차단 (자동 linking 안 함 — 도용 방지)
  - **다른 이메일** → 신규 카카오 계정 생성 (별개 customer row — 의도된 동작)

**이메일 처리**: 카카오에서 받은 이메일을 `customers.email` 에 그대로 저장. 카카오 콘솔에서 이메일 **필수 동의** 설정이 전제 (운영 영역).

---

## 4. JWT 정책

### 라이브러리

`io.jsonwebtoken:jjwt` (한국 Spring 표준):
- `jjwt-api`
- `jjwt-impl` (runtime)
- `jjwt-jackson` (runtime)

→ `build.gradle` 에 추가 ([`coding-convention.md` §9](coding-convention.md))

### 토큰 종류

| | Access Token | Refresh Token |
|---|---|---|
| **만료** | **30분** | **14일** |
| **저장소** | 클라이언트만 (stateless) | 클라이언트 + DB |
| **용도** | API 요청 인증 | Access 갱신 |
| **알고리즘** | HS256 | HS256 |

### Access Token Claim

```json
{
  "sub": "42",
  "role": "CUSTOMER",
  "iss": "magampick-api",
  "iat": 1715582400,
  "exp": 1715584200
}
```

- `sub`: 사용자 ID (정수 문자열)
- `role`: `CUSTOMER` / `SELLER` / `ADMIN`
- 사용자 식별 = `(role, sub)` 쌍 으로 함 (customer:42 와 seller:42 는 다른 사람)

⚠️ Claim 에 민감 정보 (이메일, 이름, 휴대폰) 포함 X. ID + role 만 두고 필요 시 DB 조회.

### Refresh Token

- DB 의 `refresh_tokens` 테이블에 저장 (ERD 별도 추가 필요)
- `Rotation` — 갱신 시 새 refresh 발급 + 기존 무효화
- 동일 사용자가 여러 기기 로그인 시 device 별 row 다중 보관

### Secret

- 환경 변수 `JWT_SECRET` (256bit 이상)
- 환경별 다른 키 (local / dev / prod)
- 노출 시 즉시 교체 → 전 사용자 재로그인

---

## 5. 토큰 갱신

```
POST /api/v1/auth/refresh
{ "refreshToken": "eyJ..." }
```

서버 처리:
1. JWT 서명 / 만료 검증
2. DB 에서 refresh token 존재 / 유효 확인
3. 새 access + 새 refresh 발급
4. **DB 의 기존 refresh 무효화** (rotation)

응답: access + refresh 둘 다 새 토큰.

Refresh 도 만료 → `401 TOKEN_EXPIRED` → 재로그인 필요.

---

## 6. 로그아웃

### 단일 기기 로그아웃

```
POST /api/v1/auth/logout
Authorization: Bearer {access}
```

처리:
1. DB 에서 **해당 기기의 refresh token 삭제** (토큰 자체 또는 device_id 기준)
2. **Access Token 은 stateless 라 강제 무효화 X** → 만료까지 (최대 30분) 유효
3. 클라이언트가 access / refresh 모두 폐기

### 모든 기기 로그아웃

```
DELETE /api/v1/auth/sessions
Authorization: Bearer {access}
```

해당 사용자의 모든 refresh token 삭제 → 다른 기기도 다음 갱신 시도 시 강제 로그아웃.

---

## 7. 비밀번호 정책

### 해싱
- **`BCryptPasswordEncoder`** (Spring Security 기본)
- **Cost: 12** (기본 10 보다 강함, 출시 시점 부하 보고 조정)

### 강도 요구

| 항목 | 정책 |
|---|---|
| 최소 길이 | **8자** |
| 영문 | 1개 이상 |
| 숫자 | 1개 이상 |
| 특수문자 | **1개 이상 필수** |

### 재설정 흐름

```
1. POST /api/v1/auth/password-reset/request { email }
   → 이메일로 1회용 토큰 발송 (유효 1시간)
2. 사용자: 이메일 링크 클릭 → 새 비밀번호 입력
3. POST /api/v1/auth/password-reset/confirm { token, newPassword }
4. 비밀번호 변경 + 해당 사용자의 모든 refresh token 무효화
```

---

## 8. 휴대폰 본인인증

### SMS 직접 인증 (SOLAPI) — ADR-001

외부 본인확인 모듈(PASS / NICE / KCB)은 **미사용**. 휴대폰 번호 **소유 확인**은 SMS 인증번호(OTP) 직접 발송·검증으로 처리한다 (결정 배경: 노션 "본인인증" 명세 / ADR-001). 만 14세 자기신고·phone UNIQUE 미적용·1인 1계정 미강제라 외부 모듈의 강점(CI·생년월일)이 불필요.

> 정책·scope·비즈니스 규칙(OTP 길이/만료, 발송·시도 제한, 토큰 수명)의 single source 는 **노션 "본인인증" 페이지**. 이 문서엔 구조·연동 결정만 기록.

**DB**
- `customers.phone`, `sellers.phone` 둘 다 **UNIQUE 영구 미적용**. 사유:
  - **별개 계정 모델** — 한 사람이 카카오 / 이메일+비번으로 각각 customer 계정 보유 가능 (별개 row, §3 참조)
  - **사장 다중 사업자** — 한 사람이 여러 사업자를 운영하는 비즈니스 현실 (같은 phone 으로 여러 seller 계정 자연스러움)
  - **본인인증의 의미** = "번호 소유자 검증" 이지 "1번호 1계정 강제" 가 아님 (한국 이커머스 표준 패턴: 쿠팡, 배민 모두 phone UNIQUE 없음)
- 회원가입 완료 시 `phone_verified_at` 기록

**구조** (`com.magampick.phone`)
- `PhoneVerificationService` — OTP 발송·검증, 본인인증 토큰 발급/소비, 발송·시도 제한 오케스트레이션. 단기 데이터는 **Redis**(`PhoneVerificationStore`)로 TTL·1회용 강제
- `SmsSender` 인터페이스 — 실발송과 mock 을 교체 가능. 발송 실패 시 RuntimeException → `SMS_SEND_FAILED`(502)

| 구현 | 프로파일 | 동작 |
|---|---|---|
| `MockSmsSender` | `test` | 실제 발송 대신 인증번호 로그 |
| `RealSolapiSmsSender` | `!test` (local/dev/prod) | **SOLAPI** SDK(`net.nurigo:sdk`)로 실 SMS 발송 |

> 실발송이 test 외 전 환경에서 활성 → 로컬에서도 실 문자 발송. 테스트/CI 는 `test` 프로파일이라 SOLAPI 키 없이 green. 외부연동을 test 에서만 끄는 `@Profile("!test")` 패턴은 `OciStorageConfig` 와 동일.

### 흐름

```
1. 프론트: 휴대폰 번호 입력 → "인증번호 받기"
2. POST /api/v1/auth/phone-verifications { phone }
   → 서버: 6자리 OTP 생성 → SOLAPI SMS 발송 → Redis 저장(3분). 재발송 쿨다운(30초)·일일 한도(10) 검사
3. 프론트: 받은 인증번호 입력 → 확인
4. POST /api/v1/auth/phone-verifications/confirm { phone, code }
   → 서버: OTP 검증(시도 5회 초과 시 무효화) → 성공 시 본인인증 토큰(opaque·15분·1회용) 발급
5. 프론트: 추가정보 입력 → 가입
6. POST /api/v1/auth/signup { ..., verificationToken }
   → 서버: consumeVerificationToken(토큰의 번호 == 가입 번호 + 1회용) → customers INSERT (phone_verified_at = NOW())
```

에러 코드는 `com.magampick.phone.exception.PhoneVerificationErrorCode` (`PHONE_FORMAT_INVALID`·`OTP_RESEND_LIMIT`·`OTP_DAILY_LIMIT`·`OTP_EXPIRED`·`OTP_INVALID`·`OTP_ATTEMPT_LIMIT`·`PHONE_VERIFICATION_EXPIRED`·`SMS_SEND_FAILED`). 환경 변수는 §12.

### Pending (ADR-001 후속 백로그)

- reCAPTCHA·봇 방지 정책
- VoIP·해외 번호 차단 정책 (현재는 형식 검증만)

---

## 9. 인가 (Authorization)

### URL 별 권한 매트릭스

| 패턴 | Public | Customer | Seller | Admin |
|---|---|---|---|---|
| `POST /api/v1/auth/**` (signup, login, refresh, kakao) | ✅ | | | |
| `GET /api/v1/stores`, `/clearance-items` 검색·상세 | ✅ | ✅ | ✅ | ✅ |
| `GET /api/v1/terms` 약관 목록 (가입 화면, 사장=`role=SELLER`) | ✅ | ✅ | ✅ | ✅ |
| `GET /api/v1/customers/me/**` | | ✅ | | |
| `POST /api/v1/orders`, `POST /api/v1/reviews` | | ✅ | | |
| `/api/v1/seller/**` | | | ✅ | |
| `/api/v1/admin/**` | | | | ✅ |

### Spring Security 패턴

```java
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers(GET,
                    "/api/v1/stores/**",
                    "/api/v1/clearance-items/**",
                    "/api/v1/terms/**").permitAll()
                .requestMatchers("/api/v1/seller/**").hasRole("SELLER")
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter.class)
            .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
```

메서드 레벨 권한:
```java
@PreAuthorize("hasRole('ADMIN')")
public void approveSeller(Long sellerId) { ... }
```

### 본인 리소스 접근 제어

URL 의 `/me/` 패턴 외에도, **타인 리소스 직접 ID 접근 차단** 필요:

```java
// 잘못된 예
@GetMapping("/api/v1/customers/{id}/orders")  // ID 조작으로 타인 주문 조회 가능

// 올바른 예
@GetMapping("/api/v1/customers/me/orders")    // 토큰의 sub 에서 ID 추출
```

원칙: **PathVariable 의 ID 를 신뢰하지 않고, 토큰의 sub 와 매치 확인** 또는 `/me/` 패턴 사용.

---

## 10. 보안 헤더 / 정책

### CORS

| 환경 | allowedOrigins |
|---|---|
| local | `*` (개발 편의) |
| dev | `https://dev.magampick.com`, `https://admin.dev.magampick.com` |
| prod | `https://magampick.com`, `https://admin.magampick.com` |

모바일 앱은 CORS 영향 X.

### HTTPS

- **prod 강제** (Nginx / ALB 에서 SSL termination → app 은 HTTP)
- local HTTP OK

### CSRF

**비활성** — REST API + Bearer Token. CSRF 공격 표면 없음.

### 기타 헤더

- `X-Content-Type-Options: nosniff`
- `X-Frame-Options: DENY`
- `Strict-Transport-Security` (prod 만)

---

## 11. Rate Limiting

| 엔드포인트 | 제한 | 도입 시점 |
|---|---|---|
| `POST /auth/login` | 5회 / 분 / IP | 출시 시점 |
| `POST /auth/password-reset/request` | 3회 / 시간 / 이메일 | 출시 시점 |
| `POST /auth/signup` | 3회 / 시간 / IP | 출시 시점 |
| 일반 API | 100회 / 분 / 사용자 | 트래픽 늘 때 |

**초기엔 미적용**. 도입 시 Bucket4j (in-memory) 또는 Redis 기반.

---

## 12. 환경 변수

| 키 | 용도 | 예 (local) |
|---|---|---|
| `JWT_SECRET` | 서명 키 (256bit+) | `change-me-in-prod-please-use-long-secret-key-here-256bits` |
| `JWT_ACCESS_TTL_MINUTES` | Access 만료 (분) | `30` |
| `JWT_REFRESH_TTL_DAYS` | Refresh 만료 (일) | `14` |
| `KAKAO_CLIENT_ID` | 카카오 OAuth client ID | — |
| `KAKAO_CLIENT_SECRET` | 카카오 OAuth secret | — |
| `MAIL_FROM` | 비밀번호 재설정 이메일 발신자 | `noreply@magampick.com` |
| `SOLAPI_API_KEY` | SOLAPI SMS API Key (본인인증 발송) | — (test 외 환경 필요) |
| `SOLAPI_API_SECRET` | SOLAPI SMS API Secret | — |
| `SOLAPI_SENDER_NUMBER` | SOLAPI 등록 발신번호 (숫자만) | `01000000000` |

`.env.example` 에 키 목록 유지 (값은 placeholder).

---

## 13. 에러 코드

| 상황 | HTTP | `code` |
|---|---|---|
| 토큰 없음 | `401` | `UNAUTHORIZED` |
| 토큰 만료 | `401` | `TOKEN_EXPIRED` |
| 토큰 서명 무효 | `401` | `INVALID_TOKEN` |
| 비밀번호 불일치 / 이메일 없음 | `401` | `INVALID_CREDENTIALS` |
| 권한 부족 | `403` | `FORBIDDEN` |
| 이메일 중복 (가입 시) | `409` | `EMAIL_ALREADY_EXISTS` |
| 카카오 이메일이 기존 계정과 충돌 | `409` | `EMAIL_ALREADY_REGISTERED` |
| 카카오 OAuth 인증 실패 (토큰 교환 / 조회) | `502` | `SOCIAL_AUTH_FAILED` |
| 카카오 이메일 미동의 | `400` | `KAKAO_EMAIL_REQUIRED` |
| 소셜 가입 세션 만료/무효 | `400` | `SOCIAL_TOKEN_INVALID` |
| 휴대폰 인증 미완료 | `400` | `PHONE_NOT_VERIFIED` |
패키지 기준:

- JWT / Spring Security 인프라 에러 (`UNAUTHORIZED`, `TOKEN_EXPIRED`, `INVALID_TOKEN`) 는 `global/security/exception/SecurityErrorCode` 에 정의
- 회원가입 / 로그인 비즈니스 에러 (`INVALID_CREDENTIALS`, `EMAIL_ALREADY_EXISTS`, `PHONE_NOT_VERIFIED`) 는 `auth/exception/AuthErrorCode` 에 정의
- 공통 권한 부족 (`FORBIDDEN`) 은 `global/exception/CommonErrorCode` 에 정의

도메인별 ErrorCode 분리 원칙은 [`coding-convention.md` §8](coding-convention.md) 을 따른다.

---

## 14. 패키지 위치

```
com.magampick.global.security/
├── SecurityConfig.java
├── JwtAuthenticationFilter.java
├── JwtProvider.java                 # 발급 / 검증
├── CustomUserDetails.java
├── PrincipalResolver.java           # @AuthenticationPrincipal 추출
└── exception/
    └── SecurityErrorCode.java       # JWT / Security 인프라 에러
```

도메인별 가입 / 로그인은 `auth/` 도메인 폴더:

```
com.magampick.auth/
├── controller/AuthController.java
├── service/AuthService.java
├── dto/
│   ├── LoginRequest.java
│   ├── TokenResponse.java
│   └── ...
├── repository/RefreshTokenRepository.java
└── exception/
    └── AuthErrorCode.java           # 가입 / 로그인 비즈니스 에러
```

---

## 15. Pending Decisions

- 소셜 로그인 추가 (네이버 / 구글) — 애플은 PWA only 라 불필요
- 관리자 가입 흐름 (초대 메일 / 별도 시스템 / 첫 부팅 시 시드)
- Rate Limiting 구현 방식 (Bucket4j vs Redis)
- 이메일 발송 인프라 (AWS SES / SendGrid / 자체 SMTP)
- 비밀번호 변경 시 재로그인 강제 여부 (모든 refresh 무효화)
