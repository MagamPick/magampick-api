# Spec: 사장 프로필 관리

> 이슈: #35 - https://github.com/MagamPick/magampick-api/issues/35

## 1. Context

#15 회원가입/로그인 머지로 `sellers` 엔티티는 생성됐지만, 가입 시점에 박힌 정보를 사장 본인이 수정할 경로가 없다.

`features.md` "사장 프로필 관리 — 사장 이름·연락처·사업자 정보" 항목 중, **사장 이름·연락처 수정 + 프로필 조회**를 이 이슈에서 구현한다. 사업자 정보(상호·사업장 주소 등)의 입력·검증은 매장 등록 신청 이슈(국세청 API stub)에서 일괄 처리하고, 휴대폰 본인인증 절차는 별도 본인인증 stub 이슈에서 처리한다.

## 2. Scope

### In Scope
- 사장 본인 프로필 조회 — `GET /api/v1/seller/me`
- 사장 본인 이름 수정 — `PATCH /api/v1/seller/me` (수정 가능 필드: `owner_name`)
- 사장 본인 휴대폰 변경 — `POST /api/v1/seller/me/phone`
  - 본인인증 stub 통과로 간주해 `phone` 갱신 + `phone_verified_at = now()` 함께 갱신
  - 본인인증 stub 이슈에서 `PhoneVerificationService` 등 실제 stub 객체 도입 시 이 자리를 그 호출로 교체
- `features.md` 의 해당 항목 텍스트 다듬기 (사업자 정보·휴대폰 인증 분리 명시)
- `docs/erd/tables/sellers.md` 의 `phone` / `phone_verified_at` 설명 갱신

### Out of Scope (다른 이슈)
- 사업자 정보 (상호·사업장 주소 등) 입력·수정 → **매장 등록 신청** 이슈
- 휴대폰 본인인증 객체 도입 (인증번호 발송/검증 stub) → **본인인증 stub** 이슈
- 비밀번호 변경, 이메일 변경
- 관리자가 사장 프로필 조회 → 매장 승인/반려 이슈에서 자연스럽게 다룸
- 사장 프로필 사진 (`features.md` 항목에 없음)
- 회원 탈퇴

## 3. User Roles

- **Seller (사장)** — 본인 프로필 조회·수정
- Customer / Admin — 해당 없음

## 4. API Specification

> 모든 성공/실패 응답은 전역 `ApiResponse<T>` envelope 으로 감싼다. 아래 명세는 `data` payload 기준.

### 공통 응답 DTO — `SellerProfileResponse`

| 필드 | 타입 | 설명 |
|---|---|---|
| id | number (Long) | 사장 식별자 |
| email | string | 로그인 이메일 (read-only) |
| ownerName | string | 사장 이름 |
| businessNumber | string | 사업자번호 10자리 (read-only) |
| phone | string \| null | 휴대폰 번호. 가입 직후엔 null 가능 |
| phoneVerifiedAt | string (ISO 8601 +09:00) \| null | 휴대폰 인증/변경 시각 |
| verificationStatus | string | `PENDING` / `APPROVED` / `REJECTED` |
| createdAt | string (ISO 8601 +09:00) | 가입 시각 |

> 마스킹 미적용 (이슈 §4 정책 결정 — 본인 조회).

---

### GET /api/v1/seller/me

**Description**: 로그인된 사장 본인의 프로필을 조회한다.
**Authentication**: `Bearer {JWT}`, `ROLE_SELLER`

**Response** - 200 (`SellerProfileResponse`)

**Error Responses**

| 상태 | 에러 코드 | 상황 |
|---|---|---|
| 401 | UNAUTHORIZED / TOKEN_EXPIRED / INVALID_TOKEN | 토큰 누락·만료·서명 무효 |
| 403 | FORBIDDEN | SELLER 외 역할 (customer/admin) 접근 |
| 404 | SELLER_NOT_FOUND | sellerId 미존재 또는 `deleted_at != NULL` |

**OpenAPI / Swagger**
- Controller `@Tag(name = "Seller Profile", description = "사장 본인 프로필 관리 API")`
- `@Operation(summary = "사장 본인 프로필 조회", description = "JWT 의 sellerId 에 해당하는 사장의 프로필을 반환한다.")`
- `@ApiResponse(responseCode = "200", description = "조회 성공")`, `404 description = "사장 미존재 또는 탈퇴"`
- `SellerProfileResponse` 와 각 필드에 `@Schema(description, example)` 부착

---

### PATCH /api/v1/seller/me

**Description**: 사장 본인의 수정 가능한 프로필 필드를 갱신한다. 현재는 `ownerName` 만 허용.
**Authentication**: `Bearer {JWT}`, `ROLE_SELLER`

**Request Body** (`SellerProfileUpdateRequest`)

| 필드 | 타입 | 제약 | 설명 |
|---|---|---|---|
| ownerName | string | required, `@NotBlank`, `@Size(min=1, max=20)` | 새 사장 이름 |

> 알 수 없는 필드는 Jackson 기본 동작으로 silently ignore (`spring.jackson.deserialization.fail-on-unknown-properties=false`). `email` / `businessNumber` 등을 body 에 보내도 무시되며 갱신 X.

**Response** - 200 (`SellerProfileResponse`) — 갱신 후 전체 프로필

**Error Responses**

| 상태 | 에러 코드 | 상황 |
|---|---|---|
| 400 | INVALID_INPUT | `ownerName` 누락 / blank / 길이 초과 |
| 401 | UNAUTHORIZED / TOKEN_EXPIRED / INVALID_TOKEN | 토큰 문제 |
| 403 | FORBIDDEN | SELLER 외 역할 |
| 404 | SELLER_NOT_FOUND | sellerId 미존재 또는 `deleted_at != NULL` |

**OpenAPI / Swagger**
- `@Operation(summary = "사장 본인 이름 수정", description = "JWT 의 sellerId 에 해당하는 사장의 ownerName 을 갱신한다.")`
- `@ApiResponse(responseCode = "200", description = "수정 성공")`, `400`, `404`
- `SellerProfileUpdateRequest` 와 `ownerName` 에 `@Schema(description = "사장 이름", example = "홍길동")` + 제약

---

### POST /api/v1/seller/me/phone

**Description**: 사장 본인의 휴대폰 번호를 변경하고 본인인증 stub 통과로 간주해 `phone_verified_at` 도 갱신한다.
**Authentication**: `Bearer {JWT}`, `ROLE_SELLER`

**Request Body** (`SellerPhoneUpdateRequest`)

| 필드 | 타입 | 제약 | 설명 |
|---|---|---|---|
| phone | string | required, `@NotBlank`, `@Pattern("^010\\d{8}$")` | 숫자 11자리, `010` prefix |

**Response** - 200 (`SellerProfileResponse`) — `phone`, `phoneVerifiedAt` 이 갱신된 전체 프로필

**Error Responses**

| 상태 | 에러 코드 | 상황 |
|---|---|---|
| 400 | INVALID_INPUT | `phone` 누락 / 포맷 불일치 |
| 401 | UNAUTHORIZED / TOKEN_EXPIRED / INVALID_TOKEN | 토큰 문제 |
| 403 | FORBIDDEN | SELLER 외 역할 |
| 404 | SELLER_NOT_FOUND | sellerId 미존재 또는 `deleted_at != NULL` |

**OpenAPI / Swagger**
- `@Operation(summary = "사장 본인 휴대폰 변경", description = "본인인증 stub 을 통과한 새 휴대폰 번호로 갱신한다. phone_verified_at 도 함께 갱신.")`
- `@ApiResponse(responseCode = "200", description = "변경 성공")`, `400`
- `phone` 필드 `@Schema(description = "휴대폰 번호 (010 prefix, 숫자 11자리)", example = "01012345678")`

## 5. Data Model

### 새 테이블
없음.

### 기존 테이블 변경
없음. `sellers.phone`, `sellers.phone_verified_at` 컬럼은 #15 에서 이미 생성됨 (nullable, VARCHAR(20) / TIMESTAMP).

### 마이그레이션
없음. 새 V 파일 생성 X.

### ERD
- 갱신 대상: `docs/erd/tables/sellers.md`
  - `phone` 설명: "후속 본인인증 이슈에서 사용" → "사장 프로필에서 수정 가능. 변경 시 본인인증 stub 통과로 간주."
  - `phone_verified_at` 설명: "후속 본인인증 완료 시각" → "휴대폰 변경 시 함께 갱신되는 본인인증 통과 시각."
- `docs/erd/overview.md` 갱신: 불필요 (기존 결정과 일치).

## 6. Business Logic

### Processing Flow

**프로필 조회** (`GET /api/v1/seller/me`)
1. JWT 필터 통과 후 `@AuthenticationPrincipal CustomUserDetails` 에서 `sellerId = userDetails.getUserId()` 추출
2. `sellerRepository.findById(sellerId)` 조회
3. 없거나 `seller.isDeleted()` 면 `BusinessException(SellerErrorCode.SELLER_NOT_FOUND)` (404)
4. `SellerMapper.toProfileResponse(seller)` 로 변환해 반환

**이름 수정** (`PATCH /api/v1/seller/me`)
1. JWT 에서 sellerId 추출
2. `SellerProfileUpdateRequest` `@Valid` 검증 (`@NotBlank`, `@Size(1,20)`)
3. `sellerRepository.findById(sellerId)` → 없거나 deleted → `SELLER_NOT_FOUND`
4. `seller.changeOwnerName(request.ownerName())` (Entity 비즈니스 메서드)
5. JPA dirty checking 으로 UPDATE → `updated_at` 자동 갱신 (`BaseEntity`)
6. 갱신된 seller 로 `SellerProfileResponse` 반환

**휴대폰 변경** (`POST /api/v1/seller/me/phone`)
1. JWT 에서 sellerId 추출
2. `SellerPhoneUpdateRequest` `@Valid` 검증 (`@NotBlank`, `@Pattern("^010\\d{8}$")`)
3. `sellerRepository.findById(sellerId)` → 없거나 deleted → `SELLER_NOT_FOUND`
4. `seller.changePhone(request.phone(), LocalDateTime.now())` — Entity 가 `phone`, `phoneVerifiedAt` 동시 갱신 (본인인증 stub 통과 간주)
5. JPA dirty checking 으로 UPDATE
6. 갱신된 seller 로 `SellerProfileResponse` 반환

### Validation Rules
- `ownerName`: 1~20자, 공백만(`isBlank`) 불가 — `@NotBlank @Size(min=1, max=20)`
- `phone`: 정규식 `^010\d{8}$` — 숫자 11자리, `010` 접두
- 인가: SecurityConfig 의 `/api/v1/seller/**` → `hasRole("SELLER")` 매처가 customer/admin/익명 토큰 차단
- 본인 보호: URL 에 sellerId 노출 X (`/me`), JWT 의 sub 만 신뢰 (`auth.md §9 본인 리소스 접근 제어`)
- soft-deleted (`deleted_at != NULL`) 차단

### State Transition
해당 없음 — 단순 필드 갱신.

### Error Cases

| 상황 | 예외 | HTTP |
|---|---|---|
| 토큰 없음/만료/서명 무효 | (Spring Security) `JwtAuthenticationEntryPoint` 위임 | 401 |
| SELLER 외 역할 | `JwtAccessDeniedHandler` | 403 |
| sellerId 미존재 | `BusinessException(SellerErrorCode.SELLER_NOT_FOUND)` | 404 |
| soft-deleted seller | `BusinessException(SellerErrorCode.SELLER_NOT_FOUND)` | 404 |
| `ownerName` 누락/공백/길이 초과 | `MethodArgumentNotValidException` → `INVALID_INPUT` | 400 |
| `phone` 누락/포맷 불일치 | `MethodArgumentNotValidException` → `INVALID_INPUT` | 400 |

### Edge Cases
- **같은 값으로 갱신**: `ownerName` / `phone` 을 기존 값과 동일하게 보내도 정상 처리 (UPDATE 발생, `updated_at` 갱신). 별도 차단 X.
- **PATCH body 의 추가 필드**: Jackson 기본 정책으로 silently ignore. `email` / `businessNumber` / `verificationStatus` 등을 body 에 넣어도 갱신 X.
- **휴대폰 변경 → 인증 상태 갱신**: `phoneVerifiedAt` 가 항상 `LocalDateTime.now()` 로 덮어쓰여진다. stub 단계라 별도 검증 객체 호출 없음.
- **`PENDING` 상태 사장**: SELLER_NOT_APPROVED 는 매장 등록 등 핵심 기능에서만 차단 (`auth.md §13`). 프로필 조회/수정은 가입 직후부터 허용 — 본 이슈 §4 정책 결정과 일치.
- **soft-deleted seller 가 발급받은 토큰**: 토큰은 유효 (stateless) 하므로 필터는 통과하지만, Service 단에서 `SELLER_NOT_FOUND` 로 차단.

### Side Effects
- 알림 / 외부 호출 / 이벤트 발행 없음.
- 본인인증 stub 객체 도입 후 휴대폰 변경에 `PhoneVerificationService.verify(...)` 호출 1줄이 추가될 자리만 둠 (이번 이슈에선 호출 X).

### Test Cases

#### Service 단위 테스트 — `SellerServiceTest`
- `프로필_조회_성공`
- `프로필_조회_실패_sellerId_미존재`
- `프로필_조회_실패_삭제된_seller`
- `이름_수정_성공_갱신된_프로필_반환`
- `이름_수정_실패_sellerId_미존재`
- `이름_수정_실패_삭제된_seller`
- `휴대폰_변경_성공_phoneVerifiedAt_도_갱신됨`
- `휴대폰_변경_실패_sellerId_미존재`
- `휴대폰_변경_실패_삭제된_seller`

#### Controller @WebMvcTest — `SellerControllerTest`
- `GET_seller_me_200_성공`
- `GET_seller_me_401_미인증`
- `GET_seller_me_403_고객_역할`
- `GET_seller_me_404_미존재_sellerId`
- `PATCH_seller_me_200_성공`
- `PATCH_seller_me_400_ownerName_누락`
- `PATCH_seller_me_400_ownerName_길이_초과`
- `PATCH_seller_me_403_고객_역할`
- `POST_seller_me_phone_200_성공`
- `POST_seller_me_phone_400_phone_누락`
- `POST_seller_me_phone_400_phone_포맷_불일치`
- `POST_seller_me_phone_403_고객_역할`

## 7. External Dependencies
없음. 본인인증 stub 객체 도입은 별도 이슈.

## 8. Implementation Notes

- **패키지 구조**: 기존 `com.magampick.seller.{domain, repository}` 에 `controller`, `service`, `dto`, `mapper`, `exception` 추가
  - `seller/controller/SellerController.java` — `@RequestMapping("/api/v1/seller")`
  - `seller/service/SellerService.java`
  - `seller/dto/SellerProfileResponse.java`, `SellerProfileUpdateRequest.java`, `SellerPhoneUpdateRequest.java`
  - `seller/mapper/SellerMapper.java` (MapStruct, `toProfileResponse(Seller)`)
  - `seller/exception/SellerErrorCode.java` — `SELLER_NOT_FOUND(HttpStatus.NOT_FOUND, "SELLER_NOT_FOUND", "사장 계정을 찾을 수 없습니다")`
- **Entity 비즈니스 메서드**: `Seller` 에 추가
  - `void changeOwnerName(String newOwnerName)` — null/blank 가드 (서비스 단 `@Valid` 와 이중 방어)
  - `void changePhone(String newPhone, LocalDateTime verifiedAt)` — `phone`, `phoneVerifiedAt` 동시 갱신
- **트랜잭션 경계**: `SellerService` 클래스 `@Transactional(readOnly = true)`, 수정 메서드는 메서드 단 `@Transactional` override (#15 의 `AuthService` 패턴과 동일)
- **인증 주체 추출**: Controller 메서드 시그니처에 `@AuthenticationPrincipal CustomUserDetails userDetails` 사용 → `userDetails.getUserId()` 가 sellerId
- **DTO 변환**: 모두 MapStruct `SellerMapper` 로 통일. record 내 `toEntity()` / `from()` 두지 않음 (coding-convention §6)
- **에러 코드 위치**: `SELLER_NOT_FOUND` 는 도메인별 분리 원칙(coding-convention §8)에 따라 `seller/exception/SellerErrorCode` 에 신규 enum 정의. `AuthErrorCode` 와 분리
- **로깅** (`coding-convention §11`): Service 도메인 이벤트만 `INFO` — `"사장 이름 변경됨. sellerId={}"`, `"사장 휴대폰 변경됨. sellerId={}"`. 조회 로그 X
- **마스킹**: 미적용 (이슈 §4 정책). 응답·로그 모두 raw phone 노출
- **OpenAPI 그룹**: `SecurityConfig` 의 `/api/v1/seller/**` 매처 + `SwaggerConfig` 의 `2. Seller (사장)` 그룹에 자동 포함

---

## 함께 수정될 docs (이 이슈 PR 에 포함)

| 파일 | 변경 |
|---|---|
| `docs/features.md` | "사장 프로필 관리" 항목 텍스트 다듬기 (사업자 정보·휴대폰 인증 분리 명시) |
| `docs/erd/tables/sellers.md` | `phone` / `phone_verified_at` 설명 갱신 |
| `docs/roadmap.md` | 계층 1 stores 의 "사장 프로필 관리" 행 `상태 = 완료`, `이슈 = #35` 갱신 (`/impl` 단계에서 수행) |
