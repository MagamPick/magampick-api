# Spec: 소비자 프로필 관리

> 이슈: #40 - https://github.com/MagamPick/magampick-api/issues/40

## 1. Context

#15 회원가입/로그인 머지로 `customers` 엔티티는 생성됐지만, 가입 시점에 박힌 정보를 소비자 본인이 수정할 경로가 없다.

`features.md` "소비자 프로필 관리 — 닉네임, 사진, 전화번호" 항목 중, **닉네임 수정 + 휴대폰 변경 + 프로필 조회**를 이 이슈에서 구현한다. 휴대폰 본인인증 절차는 별도 본인인증 stub 이슈에서 처리하고, 본 이슈에선 사장 프로필(#35) 와 동일하게 본인인증 stub 통과로 간주해 phone 갱신 시 `phone_verified_at` 도 함께 갱신한다.

## 2. Scope

### In Scope
- 소비자 본인 프로필 조회 — `GET /api/v1/customers/me`
- 소비자 본인 닉네임 수정 — `PATCH /api/v1/customers/me` (수정 가능 필드: `nickname`)
- 소비자 본인 휴대폰 변경 — `POST /api/v1/customers/me/phone`
  - 본인인증 stub 통과로 간주해 `phone` 갱신 + `phone_verified_at = now()` 함께 갱신
  - 본인인증 stub 이슈에서 `PhoneVerificationService` 등 실제 stub 객체 도입 시 이 자리를 그 호출로 교체
- `features.md` 의 해당 항목 텍스트 다듬기 (사진·휴대폰 인증 분리 명시)
- `docs/erd/tables/customers.md` 의 `phone` / `phone_verified_at` 설명 갱신

### Out of Scope (다른 이슈)
- **프로필 사진** — 파일 업로드 인프라(스토리지·정책) 결정과 묶여 무거워짐. 별도 후속 이슈로 분리
- 휴대폰 본인인증 객체 도입 (인증번호 발송/검증 stub) → **본인인증 stub** 이슈
- 비밀번호 변경, 이메일 변경
- OAuth 연결/해제 (카카오 unlink 등) → 소셜 로그인 후속 이슈
- 회원 탈퇴 → **회원 탈퇴** 이슈 (계층 1 별도 행)
- 주소지 / 알림 설정 → 각각 별도 이슈 (`addresses` PR #36, `notification_settings` 후속)

## 3. User Roles

- **Customer (소비자)** — 본인 프로필 조회·수정
- Seller / Admin — 해당 없음

## 4. API Specification

> 모든 성공/실패 응답은 전역 `ApiResponse<T>` envelope 으로 감싼다. 아래 명세는 `data` payload 기준.

### 공통 응답 DTO — `CustomerProfileResponse`

| 필드 | 타입 | 설명 |
|---|---|---|
| id | number (Long) | 소비자 식별자 |
| email | string | 로그인 이메일 (read-only) |
| nickname | string | 소비자 닉네임 |
| phone | string \| null | 휴대폰 번호. 가입 직후엔 null 가능 |
| phoneVerifiedAt | string (ISO 8601 +09:00) \| null | 휴대폰 인증/변경 시각 |
| createdAt | string (ISO 8601 +09:00) | 가입 시각 |

> 마스킹 미적용 (이슈 §4 정책 결정 — 본인 조회).
> OAuth 연결 정보 (provider / providerUserId) 비노출 (이슈 §4 정책 결정 — 소셜 로그인 후속 이슈 범위).

---

### GET /api/v1/customers/me

**Description**: 로그인된 소비자 본인의 프로필을 조회한다.
**Authentication**: `Bearer {JWT}`, `ROLE_CUSTOMER`

**Response** - 200 (`CustomerProfileResponse`)

**Error Responses**

| 상태 | 에러 코드 | 상황 |
|---|---|---|
| 401 | UNAUTHORIZED / TOKEN_EXPIRED / INVALID_TOKEN | 토큰 누락·만료·서명 무효 |
| 403 | FORBIDDEN | CUSTOMER 외 역할 (seller/admin) 접근 |
| 404 | CUSTOMER_NOT_FOUND | customerId 미존재 또는 `deleted_at != NULL` |

**OpenAPI / Swagger**
- Controller `@Tag(name = "Customer Profile", description = "소비자 본인 프로필 관리 API")`
- `@Operation(summary = "소비자 본인 프로필 조회", description = "JWT 의 customerId 에 해당하는 소비자의 프로필을 반환한다.")`
- `@ApiResponse(responseCode = "200", description = "조회 성공")`, `404 description = "소비자 미존재 또는 탈퇴"`
- `CustomerProfileResponse` 와 각 필드에 `@Schema(description, example)` 부착

---

### PATCH /api/v1/customers/me

**Description**: 소비자 본인의 수정 가능한 프로필 필드를 갱신한다. 현재는 `nickname` 만 허용.
**Authentication**: `Bearer {JWT}`, `ROLE_CUSTOMER`

**Request Body** (`CustomerProfileUpdateRequest`)

| 필드 | 타입 | 제약 | 설명 |
|---|---|---|---|
| nickname | string | required, `@NotBlank`, `@Size(min=1, max=20)` | 새 닉네임. 회원가입 spec(#15) 과 동일 제약 |

> 알 수 없는 필드는 Jackson 기본 동작으로 silently ignore (`spring.jackson.deserialization.fail-on-unknown-properties=false`). `email` 등을 body 에 보내도 무시되며 갱신 X.

**Response** - 200 (`CustomerProfileResponse`) — 갱신 후 전체 프로필

**Error Responses**

| 상태 | 에러 코드 | 상황 |
|---|---|---|
| 400 | INVALID_INPUT | `nickname` 누락 / blank / 길이 초과 |
| 401 | UNAUTHORIZED / TOKEN_EXPIRED / INVALID_TOKEN | 토큰 문제 |
| 403 | FORBIDDEN | CUSTOMER 외 역할 |
| 404 | CUSTOMER_NOT_FOUND | customerId 미존재 또는 `deleted_at != NULL` |

**OpenAPI / Swagger**
- `@Operation(summary = "소비자 본인 닉네임 수정", description = "JWT 의 customerId 에 해당하는 소비자의 nickname 을 갱신한다.")`
- `@ApiResponse(responseCode = "200", description = "수정 성공")`, `400`, `404`
- `CustomerProfileUpdateRequest` 와 `nickname` 에 `@Schema(description = "닉네임", example = "마감픽유저")` + 제약

---

### POST /api/v1/customers/me/phone

**Description**: 소비자 본인의 휴대폰 번호를 변경하고 본인인증 stub 통과로 간주해 `phone_verified_at` 도 갱신한다.
**Authentication**: `Bearer {JWT}`, `ROLE_CUSTOMER`

**Request Body** (`CustomerPhoneUpdateRequest`)

| 필드 | 타입 | 제약 | 설명 |
|---|---|---|---|
| phone | string | required, `@NotBlank`, `@Pattern("^010\\d{8}$")` | 숫자 11자리, `010` prefix (사장 #35 와 동일 정규식) |

**Response** - 200 (`CustomerProfileResponse`) — `phone`, `phoneVerifiedAt` 이 갱신된 전체 프로필

**Error Responses**

| 상태 | 에러 코드 | 상황 |
|---|---|---|
| 400 | INVALID_INPUT | `phone` 누락 / 포맷 불일치 |
| 401 | UNAUTHORIZED / TOKEN_EXPIRED / INVALID_TOKEN | 토큰 문제 |
| 403 | FORBIDDEN | CUSTOMER 외 역할 |
| 404 | CUSTOMER_NOT_FOUND | customerId 미존재 또는 `deleted_at != NULL` |

**OpenAPI / Swagger**
- `@Operation(summary = "소비자 본인 휴대폰 변경", description = "본인인증 stub 을 통과한 새 휴대폰 번호로 갱신한다. phone_verified_at 도 함께 갱신.")`
- `@ApiResponse(responseCode = "200", description = "변경 성공")`, `400`
- `phone` 필드 `@Schema(description = "휴대폰 번호 (010 prefix, 숫자 11자리)", example = "01012345678")`

## 5. Data Model

### 새 테이블
없음.

### 기존 테이블 변경
없음. `customers.nickname`, `customers.phone`, `customers.phone_verified_at` 컬럼은 #15 에서 이미 생성됨 (`nickname VARCHAR(20) NOT NULL`, `phone VARCHAR(20) NULL`, `phone_verified_at TIMESTAMP NULL`).

### 마이그레이션
없음. 새 V 파일 생성 X.

### ERD
- 갱신 대상: `docs/erd/tables/customers.md`
  - `phone` 설명: "후속 본인인증 이슈에서 사용" → "소비자 프로필에서 수정 가능. 변경 시 본인인증 stub 통과로 간주."
  - `phone_verified_at` 설명: "후속 본인인증 완료 시각" → "휴대폰 변경 시 함께 갱신되는 본인인증 통과 시각."
- `docs/erd/overview.md` 갱신: 불필요 (기존 결정과 일치).

## 6. Business Logic

### Processing Flow

**프로필 조회** (`GET /api/v1/customers/me`)
1. JWT 필터 통과 후 `@AuthenticationPrincipal CustomUserDetails` 에서 `customerId = userDetails.getUserId()` 추출
2. `customerRepository.findById(customerId)` 조회
3. 없거나 `customer.isDeleted()` 면 `BusinessException(CustomerErrorCode.CUSTOMER_NOT_FOUND)` (404)
4. `CustomerMapper.toProfileResponse(customer)` 로 변환해 반환

**닉네임 수정** (`PATCH /api/v1/customers/me`)
1. JWT 에서 customerId 추출
2. `CustomerProfileUpdateRequest` `@Valid` 검증 (`@NotBlank`, `@Size(1,20)`)
3. `customerRepository.findById(customerId)` → 없거나 deleted → `CUSTOMER_NOT_FOUND`
4. `customer.changeNickname(request.nickname())` (Entity 비즈니스 메서드)
5. JPA dirty checking 으로 UPDATE → `updated_at` 자동 갱신 (`BaseEntity`)
6. 갱신된 customer 로 `CustomerProfileResponse` 반환

**휴대폰 변경** (`POST /api/v1/customers/me/phone`)
1. JWT 에서 customerId 추출
2. `CustomerPhoneUpdateRequest` `@Valid` 검증 (`@NotBlank`, `@Pattern("^010\\d{8}$")`)
3. `customerRepository.findById(customerId)` → 없거나 deleted → `CUSTOMER_NOT_FOUND`
4. `customer.changePhone(request.phone(), LocalDateTime.now())` — Entity 가 `phone`, `phoneVerifiedAt` 동시 갱신 (본인인증 stub 통과 간주)
5. JPA dirty checking 으로 UPDATE
6. 갱신된 customer 로 `CustomerProfileResponse` 반환

### Validation Rules
- `nickname`: 1~20자, 공백만(`isBlank`) 불가 — `@NotBlank @Size(min=1, max=20)` (회원가입 spec #15 과 동일)
- `phone`: 정규식 `^010\d{8}$` — 숫자 11자리, `010` 접두 (사장 #35 와 동일)
- 인가: SecurityConfig 의 `/api/v1/customers/me/**` → `hasRole("CUSTOMER")` 매처 (본 이슈에서 신규 추가, §8 참조) 가 seller/admin/익명 토큰 차단
- 본인 보호: URL 에 customerId 노출 X (`/me`), JWT 의 sub 만 신뢰 (`auth.md §9 본인 리소스 접근 제어`)
- soft-deleted (`deleted_at != NULL`) 차단

### State Transition
해당 없음 — 단순 필드 갱신.

### Error Cases

| 상황 | 예외 | HTTP |
|---|---|---|
| 토큰 없음/만료/서명 무효 | (Spring Security) `JwtAuthenticationEntryPoint` 위임 | 401 |
| CUSTOMER 외 역할 | `JwtAccessDeniedHandler` | 403 |
| customerId 미존재 | `BusinessException(CustomerErrorCode.CUSTOMER_NOT_FOUND)` | 404 |
| soft-deleted customer | `BusinessException(CustomerErrorCode.CUSTOMER_NOT_FOUND)` | 404 |
| `nickname` 누락/공백/길이 초과 | `MethodArgumentNotValidException` → `INVALID_INPUT` | 400 |
| `phone` 누락/포맷 불일치 | `MethodArgumentNotValidException` → `INVALID_INPUT` | 400 |

### Edge Cases
- **같은 값으로 갱신**: `nickname` / `phone` 을 기존 값과 동일하게 보내도 정상 처리 (UPDATE 발생, `updated_at` 갱신). 별도 차단 X.
- **PATCH body 의 추가 필드**: Jackson 기본 정책으로 silently ignore. `email` 등을 body 에 넣어도 갱신 X.
- **휴대폰 변경 → 인증 상태 갱신**: `phoneVerifiedAt` 가 항상 `LocalDateTime.now()` 로 덮어쓰여진다. stub 단계라 별도 검증 객체 호출 없음.
- **카카오 가입자 (`password_hash = NULL`)**: 프로필 조회/수정에 영향 없음. password 는 본 이슈에서 다루지 않으므로 NULL 여부와 무관하게 동일 흐름.
- **닉네임 중복**: 다른 customer 와 동일 닉네임으로 갱신해도 허용 (이슈 §4 정책 — UNIQUE 미적용). 별도 중복 검사 쿼리 X.
- **soft-deleted customer 가 발급받은 토큰**: 토큰은 유효 (stateless) 하므로 필터는 통과하지만, Service 단에서 `CUSTOMER_NOT_FOUND` 로 차단.

### Side Effects
- 알림 / 외부 호출 / 이벤트 발행 없음.
- 본인인증 stub 객체 도입 후 휴대폰 변경에 `PhoneVerificationService.verify(...)` 호출 1줄이 추가될 자리만 둠 (이번 이슈에선 호출 X).

### Test Cases

#### Service 단위 테스트 — `CustomerServiceTest`
- `프로필_조회_성공`
- `프로필_조회_실패_customerId_미존재`
- `프로필_조회_실패_삭제된_customer`
- `닉네임_수정_성공_갱신된_프로필_반환`
- `닉네임_수정_실패_customerId_미존재`
- `닉네임_수정_실패_삭제된_customer`
- `휴대폰_변경_성공_phoneVerifiedAt_도_갱신됨`
- `휴대폰_변경_실패_customerId_미존재`
- `휴대폰_변경_실패_삭제된_customer`

#### Controller @WebMvcTest — `CustomerControllerTest`
- `GET_customers_me_200_성공`
- `GET_customers_me_401_미인증`
- `GET_customers_me_403_사장_역할`
- `GET_customers_me_404_미존재_customerId`
- `PATCH_customers_me_200_성공`
- `PATCH_customers_me_400_nickname_누락`
- `PATCH_customers_me_400_nickname_길이_초과`
- `PATCH_customers_me_403_사장_역할`
- `POST_customers_me_phone_200_성공`
- `POST_customers_me_phone_400_phone_누락`
- `POST_customers_me_phone_400_phone_포맷_불일치`
- `POST_customers_me_phone_403_사장_역할`

## 7. External Dependencies
없음. 본인인증 stub 객체 도입은 별도 이슈.

## 8. Implementation Notes

- **패키지 구조**: 기존 `com.magampick.customer.{domain, repository}` 에 `controller`, `service`, `dto`, `mapper`, `exception` 추가
  - `customer/controller/CustomerController.java` — `@RequestMapping("/api/v1/customers")`
  - `customer/service/CustomerService.java`
  - `customer/dto/CustomerProfileResponse.java`, `CustomerProfileUpdateRequest.java`, `CustomerPhoneUpdateRequest.java`
  - `customer/mapper/CustomerMapper.java` (MapStruct, `toProfileResponse(Customer)`)
  - `customer/exception/CustomerErrorCode.java` — `CUSTOMER_NOT_FOUND(HttpStatus.NOT_FOUND, "CUSTOMER_NOT_FOUND", "소비자 계정을 찾을 수 없습니다")`
- **Entity 비즈니스 메서드**: `Customer` 에 추가
  - `void changeNickname(String newNickname)` — null/blank 가드 (서비스 단 `@Valid` 와 이중 방어)
  - `void changePhone(String newPhone, LocalDateTime verifiedAt)` — `phone`, `phoneVerifiedAt` 동시 갱신
- **SecurityConfig 매처 신규 추가**: 사장(`/api/v1/seller/**` → `hasRole("SELLER")`) 과 비대칭이므로 본 이슈에서 `/api/v1/customers/me/**` → `hasRole("CUSTOMER")` 매처를 `SecurityConfig.filterChain` 의 `authorizeHttpRequests` 체인에 추가한다 (seller / admin 매처 사이에 끼움). 후속 customer-only API (즐겨찾기·주문 등) 도 자동으로 이 매처에 잡혀 의도된 보호를 받는다.
- **트랜잭션 경계**: `CustomerService` 클래스 `@Transactional(readOnly = true)`, 수정 메서드는 메서드 단 `@Transactional` override (#15 의 `AuthService`, #35 의 `SellerService` 패턴과 동일)
- **인증 주체 추출**: Controller 메서드 시그니처에 `@AuthenticationPrincipal CustomUserDetails userDetails` 사용 → `userDetails.getUserId()` 가 customerId
- **DTO 변환**: 모두 MapStruct `CustomerMapper` 로 통일. record 내 `toEntity()` / `from()` 두지 않음 (coding-convention §6)
- **에러 코드 위치**: `CUSTOMER_NOT_FOUND` 는 도메인별 분리 원칙(coding-convention §8)에 따라 `customer/exception/CustomerErrorCode` 에 신규 enum 정의. `AuthErrorCode` 와 분리
- **로깅** (`coding-convention §11`): Service 도메인 이벤트만 `INFO` — `"소비자 닉네임 변경됨. customerId={}"`, `"소비자 휴대폰 변경됨. customerId={}"`. 조회 로그 X
- **마스킹**: 미적용 (이슈 §4 정책). 응답·로그 모두 raw phone 노출
- **OpenAPI 그룹**: SwaggerConfig 의 `1. Public (소비자)` 그룹 (`/api/v1/**` minus seller/admin) 에 자동 포함

---

## 함께 수정될 docs (이 이슈 PR 에 포함)

| 파일 | 변경 |
|---|---|
| `docs/features.md` | "소비자 프로필 관리" 항목 텍스트 다듬기 (사진·휴대폰 인증 분리 명시) |
| `docs/erd/tables/customers.md` | `phone` / `phone_verified_at` 설명 갱신 |
| `docs/roadmap.md` | 계층 1 users 의 "소비자 프로필 관리" 행 `상태 = 완료`, `이슈 = #40` 갱신 (`/impl` 단계에서 수행) |
