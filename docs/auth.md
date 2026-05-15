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
   { email, password, ownerName, businessNumber, phone, verificationToken }
3. 서버: sellers row 생성 (verification_status = PENDING)
4. 관리자 승인 대기 — 승인 전엔 매장 등록 등 핵심 기능 차단
5. 승인 후 정상 사용
```

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
{ "kakaoAccessToken": "..." }
```

흐름:
1. 클라이언트가 카카오 SDK 로 인가 → kakao access token
2. 서버: 카카오 API 로 사용자 정보 조회
3. 가입된 customer 있으면 로그인, 없으면 자동 가입 후 로그인
4. JWT 발급

**소셜 로그인은 소비자만**. 사장 / 관리자는 이메일 + 비밀번호.

---

## 4. JWT 정책

### 라이브러리

`io.jsonwebtoken:jjwt` (한국 Spring 표준):
- `jjwt-api`
- `jjwt-impl` (runtime)
- `jjwt-jackson` (runtime)

→ `build.gradle` 에 추가 ([`coding-convention.md` §8](coding-convention.md))

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

### 현재 단계 — 졸업 프로젝트 (Mock)

실제 외부 인증 API (PASS / NICE 등) 는 **미연동**. Mock 구현으로 흐름만 갖춤.

**DB**
- `customers.phone`, `sellers.phone` 은 **UNIQUE 제거** (시연 시 동일 번호로 customer + seller 둘 다 가입해야 하기 때문)
- 회원가입 완료 시 `phone_verified_at` 기록 (mock 통과 결과)

**구조 — 인터페이스 + 환경별 구현**

```java
public interface PhoneVerificationService {
    void sendCode(String phoneNumber);
    String verifyCode(String phoneNumber, String code);  // returns verification token
}
```

| 구현 | 환경 | 동작 |
|---|---|---|
| `MockPhoneVerificationService` | local, dev | `sendCode` = 로그만, `verifyCode` = 무조건 통과 (또는 고정값 `000000` 일치 시) |
| `RealPhoneVerificationService` (PASS/NICE 등) | prod | **미작성** — 출시 시점에 추가 |

Spring `@Profile` 로 환경별 자동 주입. `AuthService` 는 인터페이스만 의존 → 실 API 연동 시 수정 없음.

### 흐름 (Mock)

```
1. 프론트: 휴대폰 번호 입력 → "인증번호 받기"
2. POST /api/v1/auth/phone/request { phone }
   → 서버: 로그만 (Mock — 인증번호 000000)
3. 프론트: 인증번호 입력 (000000 또는 임의 6자리)
4. POST /api/v1/auth/phone/verify { phone, code }
   → 서버: Mock 무조건 통과 → verificationToken (수명 10분) 발급
5. 프론트: 이메일/비밀번호/닉네임 입력 → 가입
6. POST /api/v1/auth/signup { ..., verificationToken }
   → 서버: 토큰 검증 → customers INSERT (phone_verified_at = NOW())
```

### 출시 시점 처리 (Pending)

- 외부 API 제공사 결정 (PASS / NICE / KCB)
- `RealPhoneVerificationService` 작성 (`@Profile("prod")`)
- 환경 변수 (`PHONE_VERIFY_API_KEY` 등) 등록
- `phone` UNIQUE 제약 추가 여부 결정 — 운영 데이터 중복 정리 후
- CI / DI 저장 여부 (개인정보 최소화 vs 동일인 식별)

---

## 9. 인가 (Authorization)

### URL 별 권한 매트릭스

| 패턴 | Public | Customer | Seller | Admin |
|---|---|---|---|---|
| `POST /api/v1/auth/**` (signup, login, refresh, kakao) | ✅ | | | |
| `GET /api/v1/stores`, `/clearance-items` 검색·상세 | ✅ | ✅ | ✅ | ✅ |
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
                    "/api/v1/clearance-items/**").permitAll()
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
| `PHONE_VERIFY_API_KEY` | 본인인증 외부 API 키 | — (제공사 결정 후) |

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
| 휴대폰 인증 미완료 | `400` | `PHONE_NOT_VERIFIED` |
| 사장 미승인 (PENDING) | `403` | `SELLER_NOT_APPROVED` |

패키지 기준:

- JWT / Spring Security 인프라 에러 (`UNAUTHORIZED`, `TOKEN_EXPIRED`, `INVALID_TOKEN`) 는 `global/security/exception/SecurityErrorCode` 에 정의
- 회원가입 / 로그인 비즈니스 에러 (`INVALID_CREDENTIALS`, `EMAIL_ALREADY_EXISTS`, `PHONE_NOT_VERIFIED`, `SELLER_NOT_APPROVED`) 는 `auth/exception/AuthErrorCode` 에 정의
- 공통 권한 부족 (`FORBIDDEN`) 은 `global/exception/CommonErrorCode` 에 정의

도메인별 ErrorCode 분리 원칙은 [`coding-convention.md` §7](coding-convention.md) 을 따른다.

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

- 휴대폰 본인인증 외부 API 제공사 (PASS / NICE / KCB) — 출시 시점 결정
- `phone` UNIQUE 제약 재도입 — 출시 시점 결정 (졸업 프로젝트 단계엔 제거)
- 소셜 로그인 추가 (네이버 / 구글) — 애플은 PWA only 라 불필요
- 관리자 가입 흐름 (초대 메일 / 별도 시스템 / 첫 부팅 시 시드)
- Rate Limiting 구현 방식 (Bucket4j vs Redis)
- 이메일 발송 인프라 (AWS SES / SendGrid / 자체 SMTP)
- 비밀번호 변경 시 재로그인 강제 여부 (모든 refresh 무효화)
