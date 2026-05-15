# Spec: 회원가입/로그인

> 이슈: #15 - https://github.com/MagamPick/magampick-api/issues/15

## 1. Context

마감픽은 비회원 구매를 지원하지 않고, 소비자·사장·관리자 역할별로 접근 가능한 기능이 다르다. 따라서 상품 탐색 이후의 주문, 매장 운영, 관리자 승인 같은 핵심 기능을 구현하기 전에 인증 기반을 먼저 마련해야 한다.

이번 이슈는 소비자와 사장의 이메일·비밀번호 기반 회원가입/로그인, 역할 분리, JWT 발급·검증 기반을 구축해 이후 users, stores, products, orders 도메인이 사용할 공통 인증 흐름을 제공하는 것을 목적으로 한다.

## 2. Scope

### In Scope

- 소비자 이메일·비밀번호 회원가입 및 로그인
- 사장 이메일·비밀번호 회원가입 및 로그인
- 소비자/사장/관리자 역할 분리를 위한 `customers`, `sellers`, `admins` 기반 인증 구조
- 사장 승인 상태 필드 기반 마련
- 현재 단계에서는 사장 회원가입 시 임시로 자동 승인 처리
- JWT Access Token / Refresh Token 발급, 검증, 갱신 기반
- Refresh Token 저장소와 세션 무효화 기반
- 비밀번호 BCrypt 해싱 및 비밀번호 강도 검증
- 카카오 소셜 로그인은 실제 카카오 API 연동 없이 `OAuthProvider` 인터페이스와 Mock 구현까지만 포함
- 인증/인가 공통 보안 설정 기반 구성

### Out of Scope

- 휴대폰 본인인증 / 토스 인증
- 비밀번호 재설정 및 이메일 발송
- 소비자/사장 프로필 관리
- 매장 등록 신청 및 사업자 인증
- 관리자 승인/반려 기능
- 카카오 소셜 로그인 실제 연동
- 관리자 계정 생성/초대
- 인증 Rate Limiting
- 전체 기기 로그아웃 API

### 구현 경계

- `auth.md`의 휴대폰 본인인증 Mock 흐름은 이번 이슈에서 구현하지 않는다.
- 이번 이슈의 회원가입 API는 이메일·비밀번호 기반 인증 흐름에 필요한 최소 필드만 다룬다.
- `phone`은 회원가입 API에서 받지 않고 nullable 컬럼만 마련한다.
- 관리자 인증 구조는 `admins` 테이블과 `ROLE_ADMIN` 기반까지만 포함하고, 관리자 계정 생성 방식은 별도 이슈에서 다룬다.

## 3. User Roles

### Customer

- 이메일·비밀번호로 회원가입/로그인한다.
- 카카오 소셜 로그인 Mock 흐름을 통해 로그인할 수 있다.
- 로그인 성공 시 `ROLE_CUSTOMER` 권한의 JWT를 발급받는다.

### Seller

- 이메일·비밀번호로 회원가입/로그인한다.
- 현재 단계에서는 가입 즉시 임시 자동 승인 처리된다.
- 로그인 성공 시 `ROLE_SELLER` 권한의 JWT를 발급받는다.

### Admin

- 관리자 셀프 가입/초대/생성 흐름은 이번 이슈에서 구현하지 않는다.
- `admins` 테이블과 `ROLE_ADMIN` 기반은 인증 구조에 포함한다.
- 실제 관리자 계정 생성 정책은 별도 기능으로 다룬다.

## 4. API Specification

> 모든 성공/실패 응답은 전역 `ApiResponse<T>` envelope로 감싼다. 아래 명세는 `data` payload 기준이다.

### 공통 응답 DTO

`TokenResponse`

| 필드 | 타입 | 설명 |
|---|---|---|
| accessToken | string | JWT Access Token |
| refreshToken | string | JWT Refresh Token |
| accessExpiresIn | number | access token 만료까지 남은 초 단위 시간 |

### POST /api/v1/auth/signup

**Description**: 소비자 이메일·비밀번호 회원가입. 가입 성공 시 자동 로그인 처리한다.  
**Authentication**: Public

**Request Body** (`CustomerSignupRequest`)

| 필드 | 타입 | 제약 | 설명 |
|---|---|---|---|
| email | string | required, email, max 255 | 소비자 이메일. `customers.email` 내 유니크 |
| password | string | required, 8~72 | 영문/숫자/특수문자 각 1개 이상 |
| nickname | string | required, max 20 | 소비자 닉네임 |

**Response** - 201 (`TokenResponse`)

**Error Responses**

| 상태 | 에러 코드 | 상황 |
|---|---|---|
| 400 | INVALID_INPUT | 요청 값 검증 실패 |
| 409 | EMAIL_ALREADY_EXISTS | 같은 이메일의 소비자 계정이 이미 존재 |

### POST /api/v1/auth/login

**Description**: 소비자 이메일·비밀번호 로그인.  
**Authentication**: Public

**Request Body** (`LoginRequest`)

| 필드 | 타입 | 제약 | 설명 |
|---|---|---|---|
| email | string | required, email, max 255 | 소비자 이메일 |
| password | string | required | 비밀번호 |

**Response** - 200 (`TokenResponse`)

**Error Responses**

| 상태 | 에러 코드 | 상황 |
|---|---|---|
| 400 | INVALID_INPUT | 요청 값 검증 실패 |
| 401 | INVALID_CREDENTIALS | 이메일 또는 비밀번호 불일치 |

### POST /api/v1/auth/seller/signup

**Description**: 사장 이메일·비밀번호 회원가입. 현재 단계에서는 가입 즉시 임시 승인 완료 상태로 저장하고 자동 로그인 처리한다.  
**Authentication**: Public

**Request Body** (`SellerSignupRequest`)

| 필드 | 타입 | 제약 | 설명 |
|---|---|---|---|
| email | string | required, email, max 255 | 사장 이메일. `sellers.email` 내 유니크 |
| password | string | required, 8~72 | 영문/숫자/특수문자 각 1개 이상 |
| ownerName | string | required, max 20 | 사장 이름 |
| businessNumber | string | required, digits 10 | 사업자번호. 중복 체크/UNIQUE 제약 없음 |

**Response** - 201 (`TokenResponse`)

**Error Responses**

| 상태 | 에러 코드 | 상황 |
|---|---|---|
| 400 | INVALID_INPUT | 요청 값 검증 실패 |
| 409 | EMAIL_ALREADY_EXISTS | 같은 이메일의 사장 계정이 이미 존재 |

### POST /api/v1/auth/seller/login

**Description**: 사장 이메일·비밀번호 로그인.  
**Authentication**: Public

**Request Body** (`LoginRequest`)

| 필드 | 타입 | 제약 | 설명 |
|---|---|---|---|
| email | string | required, email, max 255 | 사장 이메일 |
| password | string | required | 비밀번호 |

**Response** - 200 (`TokenResponse`)

**Error Responses**

| 상태 | 에러 코드 | 상황 |
|---|---|---|
| 400 | INVALID_INPUT | 요청 값 검증 실패 |
| 401 | INVALID_CREDENTIALS | 이메일 또는 비밀번호 불일치 |

### POST /api/v1/auth/kakao

**Description**: 카카오 소셜 로그인 Mock. 실제 카카오 API를 호출하지 않고 `OAuthProvider` Mock 구현으로 소비자 로그인/자동 가입을 처리한다.  
**Authentication**: Public

**Request Body** (`KakaoLoginRequest`)

| 필드 | 타입 | 제약 | 설명 |
|---|---|---|---|
| kakaoAccessToken | string | required | Mock provider에 전달할 카카오 access token |

**Response** - 200 (`TokenResponse`)

**Error Responses**

| 상태 | 에러 코드 | 상황 |
|---|---|---|
| 400 | INVALID_INPUT | 요청 값 검증 실패 |

### POST /api/v1/auth/refresh

**Description**: refresh token rotation. 기존 refresh token을 무효화하고 새 access/refresh token을 발급한다.  
**Authentication**: Public

**Request Body** (`RefreshTokenRequest`)

| 필드 | 타입 | 제약 | 설명 |
|---|---|---|---|
| refreshToken | string | required | DB에 저장된 refresh token |

**Response** - 200 (`TokenResponse`)

**Error Responses**

| 상태 | 에러 코드 | 상황 |
|---|---|---|
| 401 | TOKEN_EXPIRED | refresh token 만료 |
| 401 | INVALID_TOKEN | 서명 무효, DB에 없음, 이미 무효화된 refresh token |

### POST /api/v1/auth/logout

**Description**: 단일 기기 로그아웃. 요청한 refresh token만 무효화한다.  
**Authentication**: Bearer Access Token

**Request Body** (`RefreshTokenRequest`)

| 필드 | 타입 | 제약 | 설명 |
|---|---|---|---|
| refreshToken | string | required | 무효화할 refresh token |

**Response** - 204: body 없음

**Error Responses**

| 상태 | 에러 코드 | 상황 |
|---|---|---|
| 401 | UNAUTHORIZED | access token 없음 |
| 401 | TOKEN_EXPIRED | access token 만료 |
| 401 | INVALID_TOKEN | access token 또는 refresh token 무효 |

## 5. Data Model

### 새 테이블

이번 이슈에서 다음 테이블을 생성한다.

- `customers`
- `sellers`
- `admins`
- `refresh_tokens`
- `customer_oauth_accounts`

카카오 Mock 로그인은 소비자 전용이므로 OAuth 계정 연결은 `customer_oauth_accounts`로만 둔다.

### `customers`

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, identity | 소비자 ID |
| email | VARCHAR(255) | NOT NULL, UNIQUE | 소비자 이메일 |
| password_hash | VARCHAR(60) | NULL | BCrypt 해시. 소셜 전용 계정은 null 가능 |
| nickname | VARCHAR(20) | NOT NULL | 소비자 닉네임 |
| phone | VARCHAR(20) | NULL | 후속 본인인증 이슈에서 사용 |
| phone_verified_at | TIMESTAMP | NULL | 후속 본인인증 이슈에서 기록 |
| deleted_at | TIMESTAMP | NULL | 소프트 삭제 |
| created_at | TIMESTAMP | NOT NULL | 생성 시각 |
| updated_at | TIMESTAMP | NOT NULL | 수정 시각 |

### `sellers`

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, identity | 사장 ID |
| email | VARCHAR(255) | NOT NULL, UNIQUE | 사장 이메일 |
| password_hash | VARCHAR(60) | NOT NULL | BCrypt 해시 |
| owner_name | VARCHAR(20) | NOT NULL | 사장 이름 |
| business_number | VARCHAR(10) | NOT NULL, digits 10 | 사업자번호. 중복 허용 |
| phone | VARCHAR(20) | NULL | 후속 본인인증 이슈에서 사용 |
| phone_verified_at | TIMESTAMP | NULL | 후속 본인인증 이슈에서 기록 |
| verification_status | VARCHAR(20) | NOT NULL, CHECK | `PENDING`, `APPROVED`, `REJECTED` |
| deleted_at | TIMESTAMP | NULL | 소프트 삭제 |
| created_at | TIMESTAMP | NOT NULL | 생성 시각 |
| updated_at | TIMESTAMP | NOT NULL | 수정 시각 |

- `business_number`에는 UNIQUE 제약을 두지 않는다.
- 이번 이슈에서는 사장 가입 시 `verification_status = APPROVED`로 저장한다.

### `admins`

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, identity | 관리자 ID |
| email | VARCHAR(255) | NOT NULL, UNIQUE | 관리자 이메일 |
| password_hash | VARCHAR(60) | NOT NULL | BCrypt 해시 |
| name | VARCHAR(20) | NOT NULL | 관리자 이름 |
| created_at | TIMESTAMP | NOT NULL | 생성 시각 |
| updated_at | TIMESTAMP | NOT NULL | 수정 시각 |

- 관리자 생성/초대/로그인 API는 이번 이슈에서 구현하지 않는다.
- 테이블과 `ROLE_ADMIN` 기반만 마련한다.

### `refresh_tokens`

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, identity | refresh token row ID |
| owner_id | BIGINT | NOT NULL | 사용자 ID |
| owner_role | VARCHAR(20) | NOT NULL, CHECK | `CUSTOMER`, `SELLER`, `ADMIN` |
| token_hash | VARCHAR(64) | NOT NULL, UNIQUE | refresh token 원문을 SHA-256 해시한 값 |
| expires_at | TIMESTAMP | NOT NULL | refresh token 만료 시각 |
| revoked_at | TIMESTAMP | NULL | 무효화 시각. null이면 유효 |
| created_at | TIMESTAMP | NOT NULL | 생성 시각 |
| updated_at | TIMESTAMP | NOT NULL | 수정 시각 |

- `owner_id + owner_role`은 polymorphic 관계라 DB FK를 두지 않고 앱 레벨에서 검증한다.
- refresh token 원문은 DB에 저장하지 않고 해시만 저장한다.
- 여러 기기 확장을 위해 사용자별 여러 row 저장을 허용한다.
- 이번 이슈의 로그아웃 API는 요청한 refresh token 1개만 무효화한다.

### `customer_oauth_accounts`

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, identity | OAuth 계정 연결 ID |
| customer_id | BIGINT | NOT NULL, FK | `customers.id` |
| provider | VARCHAR(20) | NOT NULL, CHECK | 현재는 `KAKAO` |
| provider_user_id | VARCHAR(255) | NOT NULL | provider 내부 사용자 ID |
| created_at | TIMESTAMP | NOT NULL | 생성 시각 |
| updated_at | TIMESTAMP | NOT NULL | 수정 시각 |

- `(provider, provider_user_id)`는 UNIQUE로 둔다.
- 실제 카카오 API 연동은 제외하고, Mock provider가 반환한 provider 사용자 ID를 저장한다.

### 마이그레이션

새 마이그레이션 파일을 추가한다.

```text
src/main/resources/db/migration/V{YYYYMMDDHHMMSS}__create_auth_tables.sql
```

포함 내용:

- `customers` 생성
- `sellers` 생성
- `admins` 생성
- `refresh_tokens` 생성
- `customer_oauth_accounts` 생성
- enum 성격 컬럼의 `CHECK` 제약 추가
- 이메일, refresh token hash, OAuth provider 계정에 필요한 UNIQUE 제약 추가

### ERD 문서

구현 시 다음 상세 ERD 문서를 함께 작성한다.

```text
docs/erd/tables/customers.md
docs/erd/tables/sellers.md
docs/erd/tables/admins.md
docs/erd/tables/refresh_tokens.md
docs/erd/tables/customer_oauth_accounts.md
```

### 기존 테이블 변경

없음.

## 6. Business Logic

### 6-1. 소비자 회원가입

1. `CustomerSignupRequest`를 검증한다.
2. `customers.email` 중복 여부를 확인한다.
3. 비밀번호를 `BCryptPasswordEncoder`로 해싱한다.
4. `customers` row를 생성한다.
5. access token과 refresh token을 발급한다.
6. refresh token 원문은 클라이언트에만 반환하고, DB에는 SHA-256 hash를 저장한다.
7. `201 Created`로 `TokenResponse`를 반환한다.

### 6-2. 사장 회원가입

1. `SellerSignupRequest`를 검증한다.
2. `sellers.email` 중복 여부를 확인한다.
3. `businessNumber`는 형식만 검증하고 중복 검증은 하지 않는다.
4. 비밀번호를 `BCryptPasswordEncoder`로 해싱한다.
5. `verification_status = APPROVED`로 `sellers` row를 생성한다.
6. access token과 refresh token을 발급한다.
7. refresh token hash를 DB에 저장한다.
8. `201 Created`로 `TokenResponse`를 반환한다.

### 6-3. 이메일·비밀번호 로그인

1. 소비자 로그인은 `customers`, 사장 로그인은 `sellers`에서 이메일로 계정을 조회한다.
2. 계정이 없거나 비밀번호가 일치하지 않으면 동일하게 `INVALID_CREDENTIALS`를 반환한다.
3. `deleted_at`이 null이 아닌 계정은 로그인 실패로 처리한다.
4. 사장 계정은 `verification_status != APPROVED`이면 `SELLER_NOT_APPROVED`를 반환할 수 있게 분기한다.
5. access token과 refresh token을 발급하고 refresh token hash를 DB에 저장한다.
6. `200 OK`로 `TokenResponse`를 반환한다.

### 6-4. 카카오 소셜 로그인 Mock

1. `KakaoLoginRequest.kakaoAccessToken`을 검증한다.
2. `OAuthProvider` 인터페이스를 통해 Mock 사용자 정보를 조회한다.
3. `(provider = KAKAO, provider_user_id)`로 기존 `customer_oauth_accounts`를 조회한다.
4. 기존 연결이 있으면 해당 customer로 로그인 처리한다.
5. 기존 연결이 없으면 Mock 사용자 정보로 신규 customer를 생성한다.
6. 같은 email의 customer가 이미 있으면 해당 customer에 OAuth 계정을 연결한다.
7. access token과 refresh token을 발급하고 refresh token hash를 DB에 저장한다.
8. 실제 카카오 API 호출, client id/secret 검증, redirect flow는 구현하지 않는다.

### 6-5. 토큰 갱신

1. refresh token의 JWT 서명과 만료를 검증한다.
2. refresh token 원문을 SHA-256 hash로 변환해 `refresh_tokens.token_hash`를 조회한다.
3. row가 없거나 `revoked_at`이 null이 아니면 `INVALID_TOKEN`을 반환한다.
4. JWT claim의 `(sub, role)`과 DB row의 `(owner_id, owner_role)`이 일치하는지 확인한다.
5. 기존 refresh token row의 `revoked_at`을 현재 시각으로 설정한다.
6. 새 access token과 refresh token을 발급한다.
7. 새 refresh token hash row를 저장한다.
8. `200 OK`로 새 `TokenResponse`를 반환한다.

### 6-6. 단일 기기 로그아웃

1. Authorization header의 access token을 검증해 인증 사용자를 확인한다.
2. 요청 body의 refresh token hash를 조회한다.
3. row가 없거나 이미 무효화된 경우 `INVALID_TOKEN`을 반환한다.
4. access token의 `(userId, role)`과 refresh token row의 `(owner_id, owner_role)`이 일치해야 한다.
5. 해당 refresh token row의 `revoked_at`을 현재 시각으로 설정한다.
6. `204 No Content`를 반환한다.

### 6-7. 인가 기반

- `/api/v1/auth/**`는 공개 API로 둔다.
- `/api/v1/seller/**`는 `ROLE_SELLER` 권한이 필요하다.
- `/api/v1/admin/**`는 `ROLE_ADMIN` 권한이 필요하다.
- JWT `sub`는 사용자 ID만 담고, 사용자 종류는 `role` claim으로 구분한다.
- 인증 주체는 기존 `CustomUserDetails(userId, role)`을 사용한다.

### Validation Rules

| 항목 | 정책 |
|---|---|
| email | 필수, 이메일 형식, 최대 255자 |
| password | 필수, 8~72자, 영문 1개 이상, 숫자 1개 이상, 특수문자 1개 이상 |
| nickname | 필수, 최대 20자 |
| ownerName | 필수, 최대 20자 |
| businessNumber | 필수, 숫자 10자리 |
| kakaoAccessToken | 필수 |
| refreshToken | 필수 |

### Error Cases

| 상황 | 에러 코드 | HTTP |
|---|---|---|
| 요청 검증 실패 | INVALID_INPUT | 400 |
| 이메일 중복 | EMAIL_ALREADY_EXISTS | 409 |
| 이메일/비밀번호 불일치 | INVALID_CREDENTIALS | 401 |
| access token 없음 | UNAUTHORIZED | 401 |
| token 만료 | TOKEN_EXPIRED | 401 |
| token 서명 무효/파싱 실패/DB 불일치/무효화됨 | INVALID_TOKEN | 401 |
| 사장 미승인 | SELLER_NOT_APPROVED | 403 |
| 권한 부족 | FORBIDDEN | 403 |

### Edge Cases

- 소비자와 사장은 분리 테이블이므로 같은 이메일이 각각 존재할 수 있다.
- 소비자/사장 로그인 경로를 분리해 같은 이메일의 역할 판별 모호성을 제거한다.
- `business_number`는 중복을 허용한다.
- 소셜 로그인으로 생성된 customer는 `password_hash = null`일 수 있으므로 이메일·비밀번호 로그인 대상에서 비밀번호 불일치로 처리한다.
- refresh token은 원문 저장 금지. DB에는 hash만 저장한다.
- 전체 기기 로그아웃 API는 이번 이슈에서 제공하지 않는다.
- 여러 기기 확장을 위해 refresh token row는 사용자별 다중 저장을 허용한다.

### Test Cases

#### Service 단위 테스트

- `소비자_회원가입_성공`
- `소비자_회원가입_이메일_중복시_예외`
- `소비자_회원가입_비밀번호_강도_미달시_예외`
- `사장_회원가입_성공`
- `사장_회원가입_사업자번호_중복도_허용`
- `사장_회원가입_이메일_중복시_예외`
- `소비자_로그인_성공`
- `소비자_로그인_비밀번호_불일치시_예외`
- `사장_로그인_성공`
- `사장_로그인_미승인시_예외`
- `카카오_mock_로그인_기존_계정이면_토큰_발급`
- `카카오_mock_로그인_신규_계정이면_소비자_생성`
- `토큰_갱신_성공시_refresh_token_rotation`
- `토큰_갱신_이미_무효화된_refresh_token이면_예외`
- `로그아웃_성공시_refresh_token_무효화`
- `로그아웃_다른_사용자의_refresh_token이면_예외`

#### Controller `@WebMvcTest`

- `소비자_회원가입_성공시_201`
- `소비자_회원가입_검증_실패시_400`
- `소비자_로그인_성공시_200`
- `사장_회원가입_성공시_201`
- `사장_회원가입_검증_실패시_400`
- `사장_로그인_성공시_200`
- `카카오_mock_로그인_성공시_200`
- `토큰_갱신_성공시_200`
- `로그아웃_성공시_204`

#### 통합 테스트

회원가입/로그인은 핵심 흐름이므로 다음 통합 테스트를 작성한다.

- `소비자_회원가입_후_발급받은_토큰으로_인증필요_API_접근_성공`
- `사장_회원가입_후_발급받은_토큰으로_seller_API_접근_성공`
- `refresh_token_갱신시_기존_refresh_token은_재사용_불가`

## 7. External Dependencies

### JWT

기존 `global/security/JwtProvider`와 `JwtProperties`를 사용한다.

| 설정 | 용도 | 기본값 |
|---|---|---|
| `JWT_SECRET` | JWT HS256 서명 키. 256bit 이상 | 필수 |
| `JWT_ACCESS_TTL_MINUTES` | Access Token 만료 시간 | 30 |
| `JWT_REFRESH_TTL_DAYS` | Refresh Token 만료 시간 | 14 |

### Spring Security

기존 `SecurityConfig`, `JwtAuthenticationFilter`, `CustomUserDetails`, `Role`을 확장/재사용한다.

- `PasswordEncoder`는 기존 `BCryptPasswordEncoder(12)` 빈을 사용한다.
- `/api/v1/auth/**`는 permitAll로 유지한다.
- `/api/v1/seller/**`, `/api/v1/admin/**` role 기반 보호는 기존 설정을 사용한다.

### 카카오 OAuth Mock

실제 카카오 API는 호출하지 않는다.

- `OAuthProvider` 인터페이스를 둔다.
- `MockKakaoOAuthProvider` 구현체가 고정 또는 deterministic한 사용자 정보를 반환한다.
- `KAKAO_CLIENT_ID`, `KAKAO_CLIENT_SECRET`은 이번 이슈에서 사용하지 않는다.

### 외부 인프라 제외

이번 이슈에서 다음 외부 연동은 구현하지 않는다.

- 휴대폰 본인인증 / 토스 인증
- 실제 카카오 API
- 이메일 발송
- 인증 Rate Limiting
- 관리자 계정 초대/생성 인프라

## 8. Implementation Notes

### 패키지 구조

```text
com.magampick
├── auth
│   ├── controller
│   │   └── AuthController.java
│   ├── domain
│   │   ├── RefreshToken.java
│   │   ├── CustomerOAuthAccount.java
│   │   └── OAuthProviderType.java
│   ├── dto
│   │   ├── CustomerSignupRequest.java
│   │   ├── SellerSignupRequest.java
│   │   ├── LoginRequest.java
│   │   ├── KakaoLoginRequest.java
│   │   ├── RefreshTokenRequest.java
│   │   └── TokenResponse.java
│   ├── oauth
│   │   ├── OAuthProvider.java
│   │   ├── OAuthUserInfo.java
│   │   └── MockKakaoOAuthProvider.java
│   ├── repository
│   │   ├── RefreshTokenRepository.java
│   │   └── CustomerOAuthAccountRepository.java
│   └── service
│       ├── AuthService.java
│       ├── RefreshTokenService.java
│       └── PasswordValidator.java
├── customer
│   ├── domain
│   │   └── Customer.java
│   └── repository
│       └── CustomerRepository.java
├── seller
│   ├── domain
│   │   ├── Seller.java
│   │   └── SellerVerificationStatus.java
│   └── repository
│       └── SellerRepository.java
└── admin
    ├── domain
    │   └── Admin.java
    └── repository
        └── AdminRepository.java
```

### 기존 `global/security` 변경

- 기존 `Role` enum은 그대로 사용한다.
- 기존 `JwtProvider.issueAccessToken`, `issueRefreshToken`, `parse`를 사용한다.
- `JwtProvider`에 access token 만료 초를 응답에 내려주기 위한 읽기 메서드를 추가한다.
- 기존 `global/security/exception/AuthErrorCode`에 다음 코드를 추가한다.
  - `INVALID_CREDENTIALS`
  - `EMAIL_ALREADY_EXISTS`
  - `SELLER_NOT_APPROVED`
- `BUSINESS_NUMBER_ALREADY_EXISTS`, `PHONE_NOT_VERIFIED`는 이번 결정상 사용하지 않는다.

### Entity 구현

- 모든 Entity는 `BaseEntity`를 상속한다.
- `@Table(name = "...")`를 명시한다.
- Entity 생성은 생성자 레벨 `@Builder`를 사용한다.
- setter는 사용하지 않는다.
- `RefreshToken`에는 `revoke()` 비즈니스 메서드를 둔다.
- `Seller`에는 승인 상태 확인 메서드와, 현재 자동 승인을 위한 생성 기본값을 둔다.

### Refresh Token 저장

- refresh token 원문은 저장하지 않는다.
- SHA-256 hash를 `refresh_tokens.token_hash`에 저장한다.
- token hash 생성 책임은 `RefreshTokenService` 또는 별도 유틸로 둔다.
- rotation은 하나의 트랜잭션 안에서 기존 token revoke와 새 token 저장을 함께 처리한다.

### 트랜잭션

- 회원가입, 로그인, 카카오 Mock 로그인, 토큰 갱신, 로그아웃은 쓰기 작업이므로 `@Transactional`을 사용한다.
- 조회 전용 메서드는 `@Transactional(readOnly = true)`를 사용한다.
- 토큰 갱신은 기존 refresh token 무효화와 새 refresh token 저장이 원자적으로 처리되어야 한다.

### Controller 응답

- 회원가입 성공은 `201 Created`를 반환한다.
- 로그인, 카카오 Mock 로그인, 토큰 갱신은 `200 OK`를 반환한다.
- 로그아웃 성공은 `204 No Content`를 반환한다.
- 성공 envelope는 기존 `ApiResponseAdvice`가 처리하므로 Controller는 DTO를 직접 반환한다.
- `204 No Content`는 body를 반환하지 않는다.

### 테스트 구현

- Service public 메서드는 단위 테스트를 작성한다.
- AuthController endpoint는 `@WebMvcTest(AuthController.class)`로 작성한다.
- 회원가입/로그인/refresh rotation은 핵심 흐름이므로 `@SpringBootTest + MockMvc + Testcontainers` 통합 테스트를 작성한다.
- 테스트 메서드명은 한국어 + 언더바 규칙을 따른다.
- 사업자번호 중복 허용 테스트를 명시적으로 둔다.

### 문서 업데이트

구현 시 다음 문서를 함께 갱신한다.

- `docs/erd/tables/customers.md`
- `docs/erd/tables/sellers.md`
- `docs/erd/tables/admins.md`
- `docs/erd/tables/refresh_tokens.md`
- `docs/erd/tables/customer_oauth_accounts.md`
- `docs/roadmap.md`
- 필요 시 `docs/auth.md`
