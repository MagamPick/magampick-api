# Spec: 주소지 관리

> 이슈: #36 - https://github.com/MagamPick/magampick-api/issues/36

## 1. Context

### 왜 필요한가
- 마감 임박 상품 등록 시 발송되는 반경 알림의 **3순위 채널** = "설정 주소" (`policy.md §알림`). 위치 권한 거부·미사용 + 즐겨찾기 매장도 없는 소비자가 자기 생활 반경의 떨이 알림을 받기 위한 백업 채널.

### 위치 / 범위 한정
- 계층 1-A (users 완성) 갈래. `customers` 1:N 종속 데이터.
- 본 이슈는 **알림 fallback 까지**가 명시적 용도. 탐색·매장 추천에 주소지를 어떻게 활용할지는 계층 4 (홈 피드 / 지도 조회) 이슈에서 별도 결정.
- 좌표 변환은 **클라이언트 위젯이 처리** (서버는 위경도를 받기만). 외부 지오코딩 제공자(카카오/네이버/구글) 선택은 출시 시점 결정 — 본 이슈 설계는 제공자 무관.
- **입력 UX = 주소 검색 위젯만 지원** (사용자가 검색 결과에서 고정 주소를 선택). 지도 좌표 픽 / 임시 위치 등록 UX 는 본 이슈 out of scope — 필요해지면 별도 이슈. `road_address NOT NULL` 정책은 이 가정에 기반.

## 2. Scope

### In Scope
- `addresses` 테이블 신규 생성 (마이그레이션 + Entity + ERD 상세)
- 주소지 CRUD API: 등록 / 목록 조회 / 수정 / 삭제 / 기본 주소지 변경
- 최대 3개 제약 — 초과 등록 시 거부 (400)
- PostGIS `GEOGRAPHY(POINT, 4326)` 좌표 저장 + GIST 인덱스
- 인증된 customer 만 본인 주소지 접근 (인가)

### Out of Scope (별도 이슈)
- 주소지를 탐색 / 홈 피드 / 매장 추천 기준으로 쓰는 흐름 → 계층 4
- 알림 발송 트리거에서 주소지 좌표 사용 → 계층 8-A (마감 임박 상품 등록 알림 발송)
- 사장(`sellers`) / 관리자(`admins`) 주소지 — `customers` 전용
- 외부 지오코딩 API 직접 연동 (서버 측) — 클라이언트 위젯이 위경도 공급
- 카카오 주소검색 위젯 자체 (프론트엔드 책임)
- 회원가입 시 주소 입력 강제 — 옵셔널 0~3개 유지. 가입 흐름(#15) 미수정

## 3. User Roles

### Customer (소비자)
- 본인 주소지 등록 / 목록 조회 / 수정 / 삭제 / 기본 주소지 변경
- 본인 외 주소지 접근 불가 (JWT subject 의 `customer_id` 와 row 의 `customer_id` 매칭 검증, 불일치 시 403)

### Seller / Admin
- 사용 안 함 — 주소지는 `customers` 전용 기능

## 4. API Specification

> 모든 성공/실패 응답은 전역 `ApiResponse<T>` envelope 로 감싼다. 아래 명세는 `data` payload 기준.
> 모든 endpoint 는 `/api/v1/customers/me/addresses` prefix — JWT subject 의 `customer_id` 만 사용 (PathVariable 로 customerId 받지 않음, [auth.md §9 본인 리소스 접근 제어](../auth.md)).
> 모든 endpoint Authentication: `ROLE_CUSTOMER` 필수. 토큰 누락/만료/무효 시 401, role 불일치 시 403 — Spring Security 가 처리.

### 공통 응답 DTO

`AddressResponse` (단일 주소지 표현)

| 필드 | 타입 | 설명 |
|---|---|---|
| id | number (Long) | 주소지 식별자 |
| label | string | 사용자 지정 라벨 (예: "집", "회사", "엄마집") |
| roadAddress | string | 도로명 주소 |
| jibunAddress | string \| null | 지번 주소 (카카오 위젯이 공급하지 않으면 null) |
| detailAddress | string \| null | 상세 주소 (동/호수 등 사용자 직접 입력) |
| zonecode | string \| null | 우편번호 (5자리) |
| latitude | number | 위도 (`-90 ~ 90`) |
| longitude | number | 경도 (`-180 ~ 180`) |
| isDefault | boolean | 기본 주소지 여부 |
| createdAt | string (ISO 8601 KST) | 생성 시각 |
| updatedAt | string (ISO 8601 KST) | 수정 시각 |

### POST /api/v1/customers/me/addresses

**Description**: 본인 주소지 등록. 보유 주소가 0개면 자동으로 `isDefault = true` 설정. 1개 이상이면 `isDefault = false` 로 저장 (요청 본문의 `isDefault` 값 무시 — default 변경은 PATCH 로).
**Authentication**: `ROLE_CUSTOMER`

**Request Body** (`AddressCreateRequest`)

| 필드 | 타입 | 제약 | 설명 |
|---|---|---|---|
| label | string | required, `@NotBlank`, 1~20 자 | 사용자 지정 라벨. 빈 문자열·공백만 입력 금지 |
| roadAddress | string | required, `@NotBlank`, 1~200 자 | 도로명 주소. 카카오 위젯이 공급 |
| jibunAddress | string \| null | optional, max 200 자 | 지번 주소. 카카오 위젯이 공급 |
| detailAddress | string \| null | optional, max 100 자 | 상세 주소 (사용자 직접 입력) |
| zonecode | string \| null | optional, `^[0-9]{5}$` (정확히 5자리) | 우편번호 |
| latitude | number | required, `-90 ~ 90` (inclusive) | 위도. 클라이언트가 위젯 응답에서 추출해 전송 |
| longitude | number | required, `-180 ~ 180` (inclusive) | 경도. 클라이언트가 위젯 응답에서 추출해 전송 |

**Response** - 201 Created (`AddressResponse`)
- `Location: /api/v1/customers/me/addresses/{id}` 헤더 동봉

**Error Responses**

| 상태 | 에러 코드 | 상황 |
|---|---|---|
| 400 | `INVALID_INPUT` | 요청 값 검증 실패 (`@Valid` 위반) |
| 400 | `ADDRESS_LIMIT_EXCEEDED` | 이미 3개 보유 — 4번째 등록 시도 |
| 401 | `UNAUTHORIZED` / `TOKEN_EXPIRED` / `INVALID_TOKEN` | JWT 누락/만료/무효 |
| 403 | `FORBIDDEN` | `ROLE_CUSTOMER` 아님 |

**OpenAPI / Swagger**
- Controller `@Tag(name = "Address", description = "소비자 주소지 관리")`
- `@Operation(summary = "주소지 등록", description = "본인 주소지를 등록한다. 최대 3개까지 보유 가능. 첫 등록 시 자동으로 기본 주소지로 지정된다.")`
- `@ApiResponse(responseCode = "201", description = "등록 성공")`
- `@ApiResponse(responseCode = "400", description = "검증 실패 또는 보유 한도 초과")`
- DTO `@Schema(description = "주소지 등록 요청")`, 각 필드에 `@Schema(description, example)` (예시 — label="집", roadAddress="서울특별시 강남구 테헤란로 427", latitude=37.5066, longitude=127.0535, zonecode="06158")

### GET /api/v1/customers/me/addresses

**Description**: 본인 주소지 전체 조회. 0~3개. 페이지네이션 없이 전체 반환 (최대 3개 보장).
**Authentication**: `ROLE_CUSTOMER`

**Response** - 200 OK (`List<AddressResponse>`)
- 정렬: `is_default DESC, created_at ASC` (default 가 맨 위, 그 외 등록 순서대로)

**Error Responses**

| 상태 | 에러 코드 | 상황 |
|---|---|---|
| 401 | `UNAUTHORIZED` / `TOKEN_EXPIRED` / `INVALID_TOKEN` | JWT 누락/만료/무효 |
| 403 | `FORBIDDEN` | `ROLE_CUSTOMER` 아님 |

**OpenAPI / Swagger**
- `@Operation(summary = "주소지 목록 조회", description = "본인이 등록한 주소지 0~3개를 조회한다. 기본 주소지가 가장 위에 온다.")`
- `@ApiResponse(responseCode = "200", description = "조회 성공")`

### PATCH /api/v1/customers/me/addresses/{addressId}

**Description**: 본인 주소지 부분 수정 (라벨 / 주소 / 좌표). Partial update — 명시된 필드만 갱신. **기본 주소지 변경은 별도 endpoint** (`POST .../default`) 로 분리 — PATCH 는 `isDefault` 를 받지 않음.
**Authentication**: `ROLE_CUSTOMER`

**Path Parameters**

| 파라미터 | 타입 | 설명 |
|---|---|---|
| addressId | number (Long) | 수정 대상 주소지 ID. 토큰의 `customer_id` 와 row 의 `customer_id` 불일치 시 403 |

**Request Body** (`AddressUpdateRequest`) — 모든 필드 optional, 명시된 필드만 갱신

| 필드 | 타입 | 제약 | 설명 |
|---|---|---|---|
| label | string \| null | non-null 일 때 `@NotBlank`, 1~20 자 | 라벨 변경 |
| roadAddress | string \| null | non-null 일 때 `@NotBlank`, 1~200 자 | 도로명 주소 변경 |
| jibunAddress | string \| null | optional, max 200 자 | 지번 주소 변경 |
| detailAddress | string \| null | optional, max 100 자 | 상세 주소 변경 |
| zonecode | string \| null | optional, `^[0-9]{5}$` | 우편번호 변경 |
| latitude | number \| null | optional, `-90 ~ 90` | 위도 변경. `longitude` 와 **쌍으로 함께** 전송 (한쪽만 전송 시 400) |
| longitude | number \| null | optional, `-180 ~ 180` | 경도 변경. `latitude` 와 **쌍으로 함께** 전송 |

> **Partial update 정책**: `null` 전송 = "수정 안 함" 으로 해석. 명시적 "필드 비우기" 는 본 이슈에서 지원 안 함 (label/roadAddress 는 NOT NULL 이라 비울 수 없고, 나머지 nullable 필드도 빈 값으로 만들 일이 드묾 — 필요해지면 별도 이슈로).

**Response** - 200 OK (`AddressResponse`) — 갱신된 전체 주소 정보 반환

**Error Responses**

| 상태 | 에러 코드 | 상황 |
|---|---|---|
| 400 | `INVALID_INPUT` | 검증 실패. 좌표 한 쪽만 전송 등 |
| 401 | `UNAUTHORIZED` / `TOKEN_EXPIRED` / `INVALID_TOKEN` | JWT 누락/만료/무효 |
| 403 | `FORBIDDEN` | `ROLE_CUSTOMER` 아님 또는 본인 소유 아님 |
| 404 | `ADDRESS_NOT_FOUND` | 해당 ID 의 주소지 없음 (본인 소유 아닌 경우는 403 우선 — 존재 여부 먼저 검증 후 소유자 검증) |

**OpenAPI / Swagger**
- `@Operation(summary = "주소지 수정", description = "본인 주소지의 라벨/주소/좌표를 부분 수정한다. 기본 주소지 변경은 별도 endpoint 사용.")`
- `@ApiResponse(responseCode = "200", description = "수정 성공")`
- `@ApiResponse(responseCode = "400", description = "검증 실패")`
- `@ApiResponse(responseCode = "404", description = "주소지를 찾을 수 없음")`

### POST /api/v1/customers/me/addresses/{addressId}/default

**Description**: 본인 주소지를 기본 주소지로 변경. 기존 default 는 자동으로 unset 되고 (`is_default = FALSE`), 대상 주소가 새 default 가 된다 (`is_default = TRUE`). 두 UPDATE 는 단일 트랜잭션에서 처리. **이미 default 인 주소를 다시 default 로 지정**해도 멱등하게 200 응답 (no-op, 트랜잭션 안 탐).
**Authentication**: `ROLE_CUSTOMER`

**Path Parameters**

| 파라미터 | 타입 | 설명 |
|---|---|---|
| addressId | number (Long) | 새 기본 주소지로 지정할 ID. 토큰의 `customer_id` 와 row 의 `customer_id` 불일치 시 403 |

**Request Body**: 없음

**Response** - 200 OK (`AddressResponse`) — 새 default 가 된 주소 정보 반환

**Error Responses**

| 상태 | 에러 코드 | 상황 |
|---|---|---|
| 401 | `UNAUTHORIZED` / `TOKEN_EXPIRED` / `INVALID_TOKEN` | JWT 누락/만료/무효 |
| 403 | `FORBIDDEN` | `ROLE_CUSTOMER` 아님 또는 본인 소유 아님 |
| 404 | `ADDRESS_NOT_FOUND` | 해당 ID 의 주소지 없음 |

**OpenAPI / Swagger**
- `@Operation(summary = "기본 주소지 변경", description = "지정한 주소를 기본 주소지로 설정한다. 기존 기본 주소지는 자동으로 해제된다. 이미 기본 주소지인 경우 멱등하게 처리된다.")`
- `@ApiResponse(responseCode = "200", description = "변경 성공 (또는 이미 기본 주소지인 멱등 응답)")`
- `@ApiResponse(responseCode = "404", description = "주소지를 찾을 수 없음")`

### DELETE /api/v1/customers/me/addresses/{addressId}

**Description**: 본인 주소지 hard delete. 삭제 대상이 default 였고 다른 주소가 남아있으면 **가장 오래된 주소** (`created_at ASC`) 를 자동으로 새 default 로 승계 (단일 트랜잭션). 마지막 1개 삭제 시 default 승계 대상 없음 — 그대로 삭제 (보유 주소 0개 OK).
**Authentication**: `ROLE_CUSTOMER`

**Path Parameters**

| 파라미터 | 타입 | 설명 |
|---|---|---|
| addressId | number (Long) | 삭제 대상 주소지 ID. 토큰의 `customer_id` 와 row 의 `customer_id` 불일치 시 403 |

**Response** - 204 No Content (envelope 없는 빈 응답, api-convention §4)

**Error Responses**

| 상태 | 에러 코드 | 상황 |
|---|---|---|
| 401 | `UNAUTHORIZED` / `TOKEN_EXPIRED` / `INVALID_TOKEN` | JWT 누락/만료/무효 |
| 403 | `FORBIDDEN` | `ROLE_CUSTOMER` 아님 또는 본인 소유 아님 |
| 404 | `ADDRESS_NOT_FOUND` | 해당 ID 의 주소지 없음 |

**OpenAPI / Swagger**
- `@Operation(summary = "주소지 삭제", description = "본인 주소지를 삭제한다. 기본 주소지를 삭제한 경우, 남은 주소 중 가장 오래된 것이 자동으로 기본 주소지로 승계된다.")`
- `@ApiResponse(responseCode = "204", description = "삭제 성공")`
- `@ApiResponse(responseCode = "404", description = "주소지를 찾을 수 없음")`

## 5. Data Model

### 새 테이블 — `addresses`

소비자 1:N 종속. 최대 3개 (앱 레벨 제약, DB 제약 X — 트리거 부담 회피).

| 컬럼 | 타입 | NULL | 제약 / 기본값 | 설명 |
|---|---|---|---|---|
| `id` | `BIGINT` | N | PK, `GENERATED ALWAYS AS IDENTITY` | 주소지 식별자 |
| `customer_id` | `BIGINT` | N | FK → `customers(id)` ON DELETE CASCADE | 소유 소비자 |
| `label` | `VARCHAR(20)` | N | `CHECK (length(trim(label)) >= 1)` | 사용자 지정 라벨 (자유 텍스트, enum 아님) |
| `road_address` | `VARCHAR(200)` | N | `CHECK (length(trim(road_address)) >= 1)` | 도로명 주소 |
| `jibun_address` | `VARCHAR(200)` | Y | | 지번 주소 |
| `detail_address` | `VARCHAR(100)` | Y | | 상세 주소 (사용자 직접 입력) |
| `zonecode` | `VARCHAR(10)` | Y | `CHECK (zonecode ~ '^[0-9]{5}$' OR zonecode IS NULL)` | 우편번호 5자리 |
| `location` | `GEOGRAPHY(POINT, 4326)` | N | | WGS84 위경도 좌표 |
| `is_default` | `BOOLEAN` | N | DEFAULT `FALSE` | 기본 주소지 여부 |
| `created_at` | `TIMESTAMP` | N | (BaseEntity) | 생성 시각 |
| `updated_at` | `TIMESTAMP` | N | (BaseEntity) | 수정 시각 |

> `deleted_at` 컬럼 없음 — Hard delete ([erd/overview.md "Soft Delete"](../erd/overview.md): 종속 데이터는 hard delete).
> `zonecode` 컬럼 길이는 5자리 한국 우편번호 + 미래 확장 여유 두고 `VARCHAR(10)` (다른 spec 의 `zonecode` 와 일관, 카카오 SDK 응답 그대로 저장).

### 인덱스

| 인덱스 | 컬럼 | 종류 | 목적 |
|---|---|---|---|
| `addresses_pkey` | `id` | PK | |
| `addresses_customer_id_idx` | `customer_id` | B-tree | 소비자별 주소 목록 조회 (FK 조회 패턴) |
| `addresses_customer_default_uidx` | `customer_id` WHERE `is_default = TRUE` | UNIQUE partial | customer 당 default 정확히 1개 강제 (단, 0개도 허용 — 부분 인덱스 특성) |
| `addresses_location_gix` | `location` | GIST | 미래 반경 검색 (계층 4, 8-A) 대비. 본 이슈 내 사용 없지만 PostGIS 컬럼 표준 |

> `is_default` 정합성: 부분 UNIQUE 인덱스로 "customer 당 default = 0개 또는 1개" 보장. "정확히 1개" 는 트랜잭션 로직 (등록/수정/삭제 시점) 으로 보장 — 0개일 수 있는 시점은 마지막 1개 삭제 후 (의도된 동작).

### 제약 / 무결성

- `CHECK (length(trim(label)) >= 1)` — 공백만으로 채운 label 금지
- `CHECK (length(trim(road_address)) >= 1)` — 공백만으로 채운 도로명 금지
- `CHECK (zonecode ~ '^[0-9]{5}$' OR zonecode IS NULL)` — 우편번호 5자리 숫자만
- FK `customer_id` ON DELETE CASCADE — 소비자 탈퇴 (hard delete 시) 자동 정리. 단 customers 는 soft delete 기조이므로 실제 CASCADE 발동은 드묾. customers 가 영구 삭제될 때 (탈퇴 30일 유예 후) 종속 정리

### 마이그레이션

- 파일: `src/main/resources/db/migration/V{YYYYMMDDHHMMSS}__create_addresses.sql`
- 내용: 테이블 생성 + 인덱스 4개 + CHECK 제약. PostGIS extension (`V1__init_extensions.sql`) 기존재 가정.
- V 번호는 `/impl` 시점에 `YYYYMMDDHHMMSS` 로 확정 (worktree 병렬 작업 충돌 방지, CLAUDE.md DB 규칙).

### ERD

- 작성 대상: `docs/erd/tables/addresses.md` 신규
- `docs/erd/overview.md` 의 `addresses` 블록 갱신:
  - `address` (단일 컬럼) → `road_address` / `jibun_address` / `detail_address` / `zonecode` 분리 명시
  - 다른 컬럼 (`id`, `customer_id`, `label`, `location`, `is_default`) 는 기존 표기 유지
- 새 도메인 용어 `addresses.md` 항목은 `docs/glossary.md` 에 한 줄 추가: "**주소지 (Address)** — 소비자가 등록하는 자기 위치 정보. 알림 반경 fallback 채널 및 미래 탐색 기준. 최대 3개"

## 6. Business Logic

### Processing Flow

#### 등록 (POST)
1. JWT 에서 `customer_id` 추출
2. 입력 검증 (`@Valid`): label/roadAddress 공백 금지, 좌표 범위, zonecode 형식
3. 보유 주소 개수 조회 — `count(*) WHERE customer_id = ?` → 3 이상이면 `ADDRESS_LIMIT_EXCEEDED` (400)
4. `isDefault` 결정:
   - 보유 0개 → 신규 주소를 `is_default = TRUE` 로 저장
   - 보유 1~2개 → `is_default = FALSE` 로 저장 (요청 본문의 isDefault 값 무시)
5. `latitude`/`longitude` → PostGIS Point (`new GeometryFactory().createPoint(new Coordinate(lng, lat))`, SRID 4326) → Entity location 필드
6. `INSERT` → 생성된 row → `AddressResponse` 매핑 → 201 + Location 헤더

#### 목록 조회 (GET)
1. JWT 에서 `customer_id` 추출
2. `SELECT * FROM addresses WHERE customer_id = ? ORDER BY is_default DESC, created_at ASC`
3. Entity → `AddressResponse` 매핑 → 200

#### 수정 (PATCH)
1. JWT 에서 `customer_id` 추출 + Path `addressId`
2. `findById(addressId)` → 없으면 `ADDRESS_NOT_FOUND` (404)
3. 본인 소유 검증 (`row.customer_id == jwt.customer_id`) → 불일치 시 `FORBIDDEN` (403)
4. 입력 검증:
   - 좌표는 lat/lng 쌍 검증 — XOR (한쪽만 전송) 시 `INVALID_INPUT` (400)
5. 명시 전송된 필드 (label / roadAddress / jibunAddress / detailAddress / zonecode / lat+lng) 만 UPDATE
6. 갱신된 Entity → `AddressResponse` → 200

#### 기본 주소지 변경 (POST .../default)
1. JWT 에서 `customer_id` 추출 + Path `addressId`
2. `findById(addressId)` → 없으면 `ADDRESS_NOT_FOUND` (404)
3. 본인 소유 검증 → 불일치 시 `FORBIDDEN` (403)
4. 대상이 이미 `is_default = TRUE` 면 → 그대로 `AddressResponse` 반환 (멱등 200, UPDATE skip)
5. 기존 default 조회 (`WHERE customer_id = ? AND is_default = TRUE AND id != ?`)
6. 같은 `@Transactional` 안에서:
   - 기존 default 가 있으면 → `is_default = FALSE` 로 UPDATE
   - 대상 row → `is_default = TRUE` 로 UPDATE
   - (두 UPDATE 순서는 부분 UNIQUE 인덱스 위반 회피를 위해 **기존 unset → 신규 set** 순서 유지)
7. 갱신된 Entity → `AddressResponse` → 200

#### 삭제 (DELETE)
1. JWT 에서 `customer_id` 추출 + Path `addressId`
2. `findById(addressId)` → 없으면 `ADDRESS_NOT_FOUND` (404)
3. 본인 소유 검증 → 불일치 시 `FORBIDDEN` (403)
4. 대상이 default 였는지 확인
5. `DELETE` 실행
6. 대상이 default 였다면:
   - 남은 주소 중 가장 오래된 것 조회 (`WHERE customer_id = ? ORDER BY created_at ASC LIMIT 1`)
   - 있으면 해당 row 의 `is_default = TRUE` 로 UPDATE
   - 없으면 (마지막 1개 삭제) skip — 0개 허용
7. → 204 No Content

### Validation Rules

| 필드 | 규칙 |
|---|---|
| label | NOT NULL, `@NotBlank`, length 1-20 |
| roadAddress | NOT NULL, `@NotBlank`, length 1-200 |
| jibunAddress | NULL OK, length ≤ 200 |
| detailAddress | NULL OK, length ≤ 100 |
| zonecode | NULL OK, 정확히 5자리 숫자 (`^[0-9]{5}$`) |
| latitude | NOT NULL (등록 시), `-90 ≤ x ≤ 90` |
| longitude | NOT NULL (등록 시), `-180 ≤ x ≤ 180` |
| 보유 개수 | ≤ 3 |
| 좌표 쌍 (PATCH) | latitude/longitude 둘 다 전송 또는 둘 다 미전송 |

### State Transition

`is_default` 만이 의미 있는 상태. 다른 상태 머신 없음.

| 현재 | 액션 | 결과 |
|---|---|---|
| `is_default = FALSE` (다른 주소가 default) | POST `.../default` | 기존 default → FALSE, 본 row → TRUE (트랜잭션) |
| `is_default = TRUE` | POST `.../default` | 멱등 — UPDATE 없이 200 |
| `is_default = TRUE` | DELETE | 가장 오래된 다른 주소 → TRUE 자동 승계 (트랜잭션). 다른 주소 없으면 그냥 삭제 |
| `is_default = FALSE` (보유 1개뿐, 그 1개가 default) | DELETE | 그냥 삭제. 보유 0개 OK |
| 보유 0개 | POST `/addresses` | 신규 row → `is_default = TRUE` 자동 |

### Error Cases

| 상황 | 예외 | HTTP | code |
|---|---|---|---|
| 입력 검증 실패 (`@Valid`) | `MethodArgumentNotValidException` (전역 처리) | 400 | `INVALID_INPUT` |
| 보유 한도 초과 | `BusinessException(AddressErrorCode.ADDRESS_LIMIT_EXCEEDED)` | 400 | `ADDRESS_LIMIT_EXCEEDED` |
| 좌표 쌍 불일치 (한쪽만 전송) | `BusinessException(CommonErrorCode.INVALID_INPUT)` 또는 record 컴팩트 생성자 검증 → `MethodArgumentNotValidException` 으로 통합 | 400 | `INVALID_INPUT` |
| JWT 누락/만료/무효 | Spring Security → 401 | 401 | `UNAUTHORIZED` / `TOKEN_EXPIRED` / `INVALID_TOKEN` |
| role 불일치 | Spring Security → 403 | 403 | `FORBIDDEN` |
| 본인 외 주소 접근 | `BusinessException(CommonErrorCode.FORBIDDEN)` | 403 | `FORBIDDEN` |
| 주소 없음 | `BusinessException(AddressErrorCode.ADDRESS_NOT_FOUND)` | 404 | `ADDRESS_NOT_FOUND` |

### Edge Cases

- **동시성 — default 변경 경합**: 같은 customer 가 동시에 두 `POST .../default` 로 각각 다른 주소를 default 로 변경 시도 → 부분 UNIQUE 인덱스 (`addresses_customer_default_uidx`) 가 충돌을 차단. 두 번째 트랜잭션은 DB level UNIQUE violation → `DataIntegrityViolationException` → 일반 500 으로 노출 (졸업 단계 — Race condition 빈도 극히 낮음. 출시 시점에 명시적 처리 검토). 본 이슈에선 동시성 락 (pessimistic / advisory lock) 도입 없음.
- **동시성 — 보유 한도 경합**: 동시에 2건 POST → count 검증 후 INSERT 사이 race → 4번째 row 가 들어갈 수 있음. 졸업 단계 허용. DB UNIQUE 로 강제 안 함 (UNIQUE INDEX `(customer_id, created_at)` 같은 합성 키로는 한도 강제 불가). 출시 시점 처리 검토 — advisory lock 또는 비관적 락.
- **좌표 정밀도**: PostGIS GEOGRAPHY 는 내부적으로 double precision (~15자리). 클라이언트가 보내는 소수점 6-7자리 (cm 정밀도) 는 손실 없이 저장.
- **default 자동 승계 — `created_at` 동순간**: 0.001ms 단위 충돌은 BIGINT IDENTITY `id` 가 tie-breaker (`ORDER BY created_at ASC, id ASC`) — 명시 정렬 추가.
- **label 동일 허용**: 같은 customer 가 "집" 라벨 주소 여러 개 등록 가능. UNIQUE 강제 안 함 (자유 텍스트 정책 일관).
- **수정 시 좌표 변경 ≠ 주소 변경**: 도로명만 바꾸고 좌표 그대로 둘 수 있고, 좌표만 바꿀 수도 있음. 일관성은 클라이언트 책임 (카카오 위젯이 도로명 + 좌표 함께 공급).
- **detail_address 만 변경**: roadAddress/좌표는 동일하고 동/호수만 수정하는 흔한 케이스 — PATCH 로 `detailAddress` 만 전송하면 됨.

### Side Effects

- 본 이슈에선 **없음**. 알림 발송 트리거가 좌표를 SELECT 로 사용하는 것은 계층 8-A 별도 이슈. 본 이슈 = 데이터 공급만.
- default 변경 / 자동 승계는 같은 `addresses` 테이블 내 UPDATE 만 — 외부 컴포넌트 / 이벤트 publish 없음.

### Test Cases

#### Service 단위 테스트 (`AddressServiceTest`)
- `주소지_등록_성공_첫_등록은_default_자동_지정`
- `주소지_등록_성공_두번째_부터는_default_FALSE`
- `주소지_등록_실패_보유_한도_초과`
- `주소지_등록_실패_빈_label`
- `주소지_목록_조회_성공_default_가_맨_위`
- `주소지_목록_조회_성공_보유_0개면_빈_리스트`
- `주소지_수정_성공_label_만_변경`
- `주소지_수정_성공_좌표_쌍_변경`
- `주소지_수정_실패_좌표_한쪽만_전송`
- `주소지_수정_실패_본인_외_주소`
- `주소지_수정_실패_존재하지_않음`
- `기본_주소지_변경_성공_기존_default_unset`
- `기본_주소지_변경_성공_이미_default_면_멱등`
- `기본_주소지_변경_실패_본인_외_주소`
- `기본_주소지_변경_실패_존재하지_않음`
- `주소지_삭제_성공`
- `주소지_삭제_성공_default_삭제시_가장_오래된_주소_자동_승계`
- `주소지_삭제_성공_마지막_1개_삭제시_default_승계_없이_종료`
- `주소지_삭제_실패_본인_외_주소`
- `주소지_삭제_실패_존재하지_않음`

#### Controller `@WebMvcTest` (`AddressControllerTest`)
- `POST_addresses_201_성공`
- `POST_addresses_400_label_누락`
- `POST_addresses_400_latitude_범위_초과`
- `POST_addresses_400_zonecode_형식_위반`
- `POST_addresses_400_보유_한도_초과`
- `POST_addresses_401_토큰_없음`
- `POST_addresses_403_ROLE_CUSTOMER_아님`
- `GET_addresses_200_성공`
- `GET_addresses_200_빈_리스트`
- `PATCH_addresses_200_label_변경`
- `PATCH_addresses_400_좌표_한쪽만`
- `PATCH_addresses_403_본인_외`
- `PATCH_addresses_404_없음`
- `POST_addresses_default_200_성공`
- `POST_addresses_default_200_이미_default_멱등`
- `POST_addresses_default_403_본인_외`
- `POST_addresses_default_404_없음`
- `DELETE_addresses_204_성공`
- `DELETE_addresses_403_본인_외`
- `DELETE_addresses_404_없음`

#### 통합 테스트 (`AddressIntegrationTest`)
- `주소지_등록_후_목록_조회_default_TRUE_반환`
- `기본_주소지_변경시_기존_default_FALSE_로_unset` (POST `.../default` 트랜잭션 검증)
- `default_삭제시_가장_오래된_주소_자동_승계` (자동 승계 검증)

> 통합 테스트는 test-convention §2 🟡 권장 (핵심 비즈니스 흐름). default 변경 / 자동 승계는 트랜잭션 경계와 부분 UNIQUE 인덱스 협업이 핵심이라 통합 테스트로 검증할 가치 있음.

## 7. External Dependencies

없음. 좌표 변환은 클라이언트 (카카오 SDK) 가 처리하고 서버는 위경도 숫자만 받음. 외부 API 연동 / 환경 변수 추가 없음.

## 8. Implementation Notes

- **Entity 관계**: `Address` → `Customer` 단방향 `@ManyToOne(fetch = LAZY)`. Customer 쪽에 `@OneToMany` 컬렉션 두지 않음 (가입 시 0개 + 변경 빈도 낮음 + 컬렉션 lazy loading 위험 회피, coding-convention §3 의 양방향 관계 주의 일관).
- **PostGIS Point 타입**: `org.locationtech.jts.geom.Point` (Hibernate Spatial). Entity 필드는 `Point`, DTO 는 `Double latitude / longitude`. Mapper (MapStruct) 에서 직접 변환은 어렵기 때문에 별도 변환 헬퍼 또는 `@Mapping(target = "location", expression = "...")` 사용 — `/impl` 시점에 결정. 기본 패턴:
  ```java
  // global/common/GeometryUtil.java 같은 헬퍼 (필요 시 신규 생성)
  Point toPoint(double lat, double lng) {
      Point p = new GeometryFactory().createPoint(new Coordinate(lng, lat));
      p.setSRID(4326);
      return p;
  }
  double lat(Point p) { return p.getY(); }
  double lng(Point p) { return p.getX(); }
  ```
- **트랜잭션 경계**: 모든 service 메서드 `@Transactional` (write) / `@Transactional(readOnly = true)` (read). default 변경 / 자동 승계는 단일 트랜잭션 안에서 처리 — 부분 UNIQUE 인덱스 위반 방지.
- **외부 API 어댑터 구조**: 해당 없음 (외부 API 미사용).
- **캐시**: 미사용. 보유 ≤ 3 이라 DB 조회 부담 없음.
- **동시성**: 락 도입 없음 (졸업 단계). 자세한 사유는 §Edge Cases.
- **예외 클래스 분리**: `address/exception/AddressErrorCode.java` 신규. enum 값:
  - `ADDRESS_NOT_FOUND` (404)
  - `ADDRESS_LIMIT_EXCEEDED` (400) — message: "주소지는 최대 3개까지 등록 가능합니다"
  - 본인 소유 아닌 경우 → `CommonErrorCode.FORBIDDEN` 재사용 (인가 공통)
  - 입력 검증 실패 → `CommonErrorCode.INVALID_INPUT` 재사용
- **패키지 위치** (`coding-convention.md §1`):
  ```
  com.magampick.address/
  ├── controller/AddressController.java
  ├── service/AddressService.java
  ├── repository/AddressRepository.java
  ├── domain/Address.java                  # Entity
  ├── dto/
  │   ├── AddressCreateRequest.java
  │   ├── AddressUpdateRequest.java
  │   └── AddressResponse.java
  ├── mapper/AddressMapper.java            # MapStruct
  └── exception/AddressErrorCode.java
  ```
- **Spring Security 매칭**: `/api/v1/customers/me/**` 패턴은 기존 `auth.md §9` 의 권한 매트릭스 (`hasRole("CUSTOMER")`) 적용 받음. `SecurityConfig` 의 기존 `.anyRequest().authenticated()` 정책으로 커버되지만, 명시적 `requestMatchers("/api/v1/customers/me/**").hasRole("CUSTOMER")` 추가 권장 (역할 명시화). `/impl` 시점에 기존 `SecurityConfig` 수정 여부 결정.
- **`@AuthenticationPrincipal` / `PrincipalResolver`**: JWT subject 의 `customer_id` 추출은 기존 `PrincipalResolver` (auth.md §14) 패턴 재사용. 신규 도입 없음.
- **MapStruct Mapper — Point 변환**: 위 `GeometryUtil` 의 static 메서드를 `@Mapping(target = "latitude", expression = "java(...)")` 로 호출하거나, `uses = {GeometryUtil.class}` 로 지정해 자동 변환. `/impl` 시점에 가장 깔끔한 패턴 채택.
