# API Convention

REST API 설계 컨벤션. 예외/에러 응답 구현은 [`coding-convention.md`](coding-convention.md) 의 예외 처리 섹션 참조.

---

## 1. 기본 원칙

- **RESTful** — 리소스 중심, HTTP 메서드로 동작 표현
- **JSON** — 요청/응답 본문은 JSON
- **camelCase** — 필드 네이밍 (Jackson 기본)

---

## 2. URL 패턴

### 형식

```
/api/v{버전}/{복수형 resource}
```

- 버전: URL prefix 로 명시 (`/api/v1`)
- 리소스: **복수형**, 다단어는 **kebab-case**
- 계층: `/{resource}/{id}/{sub-resource}`

### 예시

| 동작 | URL |
|---|---|
| 매장 목록 | `GET /api/v1/stores` |
| 매장 상세 | `GET /api/v1/stores/{storeId}` |
| 매장 등록 | `POST /api/v1/stores` |
| 매장 수정 | `PATCH /api/v1/stores/{storeId}` |
| 매장 삭제 | `DELETE /api/v1/stores/{storeId}` |
| 매장의 상품 목록 | `GET /api/v1/stores/{storeId}/products` |
| 마감 임박 상품 목록 | `GET /api/v1/clearance-items` |
| 내 즐겨찾기 추가 | `POST /api/v1/customers/me/favorites` |

### 액션형 URL

리소스 매핑이 어색한 상태 전이는 `/{resource}/{id}/{action}` 형태 허용.

| 동작 | URL |
|---|---|
| 사장의 주문 수락 | `POST /api/v1/seller/orders/{orderId}/accept` |
| 주문 취소 | `POST /api/v1/orders/{orderId}/cancel` |
| 마감 임박 상품 수동 마감 | `POST /api/v1/seller/clearance-items/{itemId}/close` |

### 역할별 prefix

같은 자원이라도 역할별로 응답·권한이 다른 경우 prefix 로 분리.

| Prefix | 대상 | 예 |
|---|---|---|
| (없음) | 소비자 (기본) | `GET /api/v1/stores` — 영업 중 매장 검색 |
| `/seller` | 사장 (자기 매장/주문) | `GET /api/v1/seller/stores` — 자기 매장만 |
| `/admin` | 관리자 | `GET /api/v1/admin/stores` — 승인 대기 포함 전체 |
| `/customers/me` 또는 `/me` | 본인 정보 | `GET /api/v1/customers/me/favorites` |

원칙:
- 소비자용 API 는 prefix 없음 (기본)
- 권한별 응답/필터가 다르면 prefix 분리

---

## 3. HTTP 메서드

| 메서드 | 용도 | 멱등성 |
|---|---|---|
| `GET` | 조회 | ✅ |
| `POST` | 생성 / 액션 | ❌ |
| `PUT` | 전체 교체 | ✅ |
| `PATCH` | 부분 수정 | ❌ |
| `DELETE` | 삭제 | ✅ |

수정은 기본 **`PATCH`** 사용. `PUT` 은 정말 전체 교체 의미 있는 경우만.

---

## 4. HTTP 상태 코드

기본적으로 **RESTful 표준** 을 따른다 (200/201/204/400/401/404/500 등). 우리 프로젝트의 결정 사항만 명시:

- **권한 부족**: `403 Forbidden` (인증은 됐으나 권한 없음). 보안 목적으로 `404` 로 가리지 않음 — 일관성 우선
- **비즈니스 룰 위반**: `409 Conflict` (이미 취소된 주문, 중복 신청, 마감된 상품 주문 등)
- **검증 실패**: `400 Bad Request` 로 통일. `422 Unprocessable Entity` 미사용
- **DELETE 성공**: `204 No Content`, envelope 없는 빈 응답

---

## 5. 응답 포맷

모든 응답은 통일 envelope (`success` + `data` 또는 `error`) 로 감싼다. `ResponseBodyAdvice` 로 자동 wrap.

- 성공 → `{ success: true, data: ... }` (`error` 필드 없음)
- 실패 → `{ success: false, error: ... }` (`data` 필드 없음)
- `@JsonInclude(NON_NULL)` 로 null 필드 omit

### 성공 응답

```json
// 단일 리소스 (200 OK)
{
  "success": true,
  "data": {
    "id": 1,
    "name": "동네빵집",
    "address": "서울시 ..."
  }
}
```

### 목록 응답

페이지네이션 응답(`PageResponse<T>` 또는 `SliceResponse<T>`)도 envelope 안의 `data` 에 들어간다. 예시:

```json
{
  "success": true,
  "data": {
    "content": [
      { "id": 1, "name": "..." },
      { "id": 2, "name": "..." }
    ],
    "page": 0,
    "size": 20,
    "totalCount": 42,
    "totalPages": 3,
    "hasNext": true,
    "hasPrevious": false
  }
}
```

> 구조 상세 (`PageResponse` vs `SliceResponse`) 는 아래 §6 참조.

### 생성 응답 (201 Created)

```
HTTP/1.1 201 Created
Location: /api/v1/stores/1

{
  "success": true,
  "data": {
    "id": 1,
    "name": "동네빵집"
  }
}
```

### 삭제 응답 (204 No Content)

본문 없음 (envelope 도 없음).

### 에러 응답

```json
{
  "success": false,
  "error": {
    "code": "STORE_NOT_FOUND",
    "message": "매장을 찾을 수 없습니다",
    "timestamp": "2026-05-13T10:00:00+09:00"
  }
}
```

검증 실패 시 `fieldErrors` 자동 추가 (`@Valid` 위반):

```json
{
  "success": false,
  "error": {
    "code": "INVALID_INPUT",
    "message": "잘못된 입력입니다",
    "timestamp": "2026-05-13T10:00:00+09:00",
    "fieldErrors": [
      { "field": "name", "message": "공백일 수 없습니다" },
      { "field": "address", "message": "필수 항목입니다" }
    ]
  }
}
```

`ApiResponse`, `ErrorResponse`, `ApiResponseAdvice`, `GlobalExceptionHandler` 구현은 [`coding-convention.md`](coding-convention.md) 참조.

---

## 6. 페이지네이션

상황에 따라 두 가지 응답 wrapper 를 사용한다.

| 용도 | 응답 타입 | 적용 예 |
|---|---|---|
| 일반 페이지네이션 (페이지 번호 이동) | `PageResponse<T>` | 관리자 화면, 정산 내역, 주문 내역 |
| 무한 스크롤 (다음 페이지 자동 로드) | `SliceResponse<T>` | 홈 피드, 매장 목록, 검색 결과, 리뷰 |

### 요청 — 공통 (Spring Data `Pageable`)

```
GET /api/v1/stores?page=0&size=20&sort=createdAt,desc
```

| 파라미터 | 설명 | 기본값 |
|---|---|---|
| `page` | 페이지 번호 (0-based) | 0 |
| `size` | 페이지 크기 | 20 |
| `sort` | `field,direction`. 여러 개 가능 | API 별 기본값 |

### 응답 — `PageResponse<T>` (일반 페이지네이션)

```json
{
  "success": true,
  "data": {
    "content": [
      { "id": 1, "name": "..." },
      { "id": 2, "name": "..." }
    ],
    "page": 0,
    "size": 20,
    "totalCount": 42,
    "totalPages": 3,
    "hasNext": true,
    "hasPrevious": false
  }
}
```

### 응답 — `SliceResponse<T>` (무한 스크롤)

```json
{
  "success": true,
  "data": {
    "content": [
      { "id": 1, "name": "..." },
      { "id": 2, "name": "..." }
    ],
    "page": 0,
    "size": 20,
    "hasNext": true
  }
}
```

`totalCount` 없음. count 쿼리 미실행 → 성능 ↑.

### Cursor 페이지네이션

성능 이슈 / 데이터 안정성 필요 시 도메인별로 별도 도입 (`?cursor=...&size=20`). **초기엔 미적용**.

구현(`PageResponse`, `SliceResponse`)은 [`coding-convention.md`](coding-convention.md) 참조.

---

## 7. 날짜 / 시간

### 정책
- **포맷**: ISO 8601
- **시간대**: 전 구간 **KST (Asia/Seoul) 통일**
- **API 응답/요청**: `2026-05-13T10:00:00+09:00` (offset 명시)
- 한국 전용 서비스(Out of Scope: 해외 사용자) 라 UTC 통일 불필요

### Jackson 직렬화 설정 (`application.yaml`)

```yaml
spring:
  jackson:
    time-zone: Asia/Seoul
    date-format: yyyy-MM-dd'T'HH:mm:ssXXX
```

→ `LocalDateTime` 필드가 JSON 응답 시 자동으로 `+09:00` offset 부착.

### 인프라 시간대 (참고)

API 정책은 아니지만 일관된 KST 동작을 위해 **운영 환경에서 함께 설정 권장**:

| 위치 | 설정 |
|---|---|
| Docker 컨테이너 (app, db) | `TZ=Asia/Seoul` env |
| PostgreSQL 컨테이너 | `PGTZ=Asia/Seoul` env |
| JDBC connection (옵션) | `serverTimezone=Asia/Seoul` |

→ `docker-compose.yml` 의 `environment` 에 명시. (컨벤션 외 영역, 인프라 작업 시 반영)

---

## 8. 필드 네이밍

| 영역 | 케이스 |
|---|---|
| JSON 필드 | camelCase (`createdAt`, `pickupTime`) |
| DB 컬럼 | snake_case (`created_at`, `pickup_time`) — Spring Boot 기본 변환 |
| URL path/query | kebab-case (`clearance-items`, `created-at`) |

엔티티/DTO 의 Java 필드명(camelCase) → Jackson → JSON(camelCase) 자동 매핑.

---

## 9. 인증

### 헤더

```
Authorization: Bearer {JWT}
```

### 주요 에러 코드 (요약)

| 상태 | 코드 | 비고 |
|---|---|---|
| 토큰 없음 | `401 Unauthorized` | `code: UNAUTHORIZED` |
| 토큰 만료 | `401 Unauthorized` | `code: TOKEN_EXPIRED` |
| 토큰 무효 (서명 오류) | `401 Unauthorized` | `code: INVALID_TOKEN` |
| 비밀번호 불일치 | `401 Unauthorized` | `code: INVALID_CREDENTIALS` |
| 권한 부족 | `403 Forbidden` | `code: FORBIDDEN` |

JWT 정책 (만료 시간, refresh, rotation, 인가 매트릭스, 비밀번호 정책 등) 상세는 **[`auth.md`](auth.md)** 참조. 구현은 `global/security/`.

---

## 10. 요청 검증

- DTO record 에 Validation 어노테이션 (`@NotBlank`, `@NotNull`, `@Size`, `@Email`)
- Controller 파라미터에 `@Valid` 부착
- 검증 실패 → `400 Bad Request` + `code: INVALID_INPUT` + `fieldErrors`

```java
@PostMapping
public ResponseEntity<StoreResponse> create(@Valid @RequestBody StoreCreateRequest request) {
    ...
}
```

---

## 11. Controller 매핑 패턴

```java
@RestController
@RequestMapping("/api/v1/stores")
@RequiredArgsConstructor
public class StoreController {

    private final StoreService storeService;

    @PostMapping
    public ResponseEntity<StoreResponse> create(@Valid @RequestBody StoreCreateRequest request) {
        StoreResponse response = storeService.create(request);
        return ResponseEntity
            .created(URI.create("/api/v1/stores/" + response.id()))
            .body(response);
    }

    @GetMapping("/{storeId}")
    public StoreResponse getOne(@PathVariable Long storeId) {
        return storeService.getOne(storeId);
    }

    @GetMapping
    public PageResponse<StoreResponse> list(Pageable pageable) {
        Page<StoreResponse> page = storeService.list(pageable);
        return PageResponse.of(page);
    }

    @PatchMapping("/{storeId}")
    public StoreResponse update(@PathVariable Long storeId,
                                @Valid @RequestBody StoreUpdateRequest request) {
        return storeService.update(storeId, request);
    }

    @DeleteMapping("/{storeId}")
    public ResponseEntity<Void> delete(@PathVariable Long storeId) {
        storeService.delete(storeId);
        return ResponseEntity.noContent().build();
    }
}
```

규칙:
- `@RestController` + `@RequestMapping("/api/v1/{resource}")` 패턴 통일
- 생성/삭제는 `ResponseEntity` 로 상태 코드/헤더 제어
- 조회/수정/목록은 객체 직접 반환 (Spring 이 200 OK 자동)
- **`ApiResponse` envelope 자동 wrap** — Controller 는 DTO/`PageResponse`/`SliceResponse` 만 반환, `ResponseBodyAdvice` 가 `{ success: true, data: ... }` 로 감싼다. envelope 직접 만들 필요 없음
- 페이지네이션은 Service 가 `Page<T>` / `Slice<T>` 반환 → Controller 에서 `PageResponse.of(...)` / `SliceResponse.of(...)` 로 변환

---

## 12. Swagger / OpenAPI

### 라이브러리

`springdoc-openapi` (Spring Boot 3.x 표준). 의존성 추가 — [`coding-convention.md`](coding-convention.md) §8 참조.

### 활성화 환경

- **local / dev 만 노출**
- **prod 차단** — `application-prod.yaml` 에서 비활성화:
  ```yaml
  springdoc:
    api-docs:
      enabled: false
    swagger-ui:
      enabled: false
  ```
- 접근: `/swagger-ui/index.html`

### 그룹 분리 (역할별)

역할별 prefix 와 일관되게 3개 그룹으로 분리. Swagger UI 상단 드롭다운으로 전환.

| 그룹 | 경로 패턴 |
|---|---|
| `1. Public (소비자)` | `/api/v1/**` (seller·admin 제외) |
| `2. Seller (사장)` | `/api/v1/seller/**` |
| `3. Admin (관리자)` | `/api/v1/admin/**` |

### `SwaggerConfig` 예시

```java
// global/config/SwaggerConfig.java
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Magampick API")
                .version("v1")
                .description("마감 임박 베이커리/카페 픽업 플랫폼"))
            .addSecurityItem(new SecurityRequirement().addList("BearerAuth"))
            .components(new Components()
                .addSecuritySchemes("BearerAuth",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")));
    }

    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
            .group("1. Public (소비자)")
            .pathsToMatch("/api/v1/**")
            .pathsToExclude("/api/v1/seller/**", "/api/v1/admin/**")
            .build();
    }

    @Bean
    public GroupedOpenApi sellerApi() {
        return GroupedOpenApi.builder()
            .group("2. Seller (사장)")
            .pathsToMatch("/api/v1/seller/**")
            .build();
    }

    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
            .group("3. Admin (관리자)")
            .pathsToMatch("/api/v1/admin/**")
            .build();
    }
}
```

→ Swagger UI 우상단 **"Authorize"** 버튼에 JWT 입력 시 모든 요청에 자동으로 `Authorization: Bearer ...` 헤더 부착.

### 어노테이션 — 표준

| 위치 | 어노테이션 | 비고 |
|---|---|---|
| **Controller 클래스** | `@Tag(name, description)` | 도메인 / 역할 단위로 부착 |
| **Controller 메서드** | `@Operation(summary, description?)` | summary 필수, description 은 비즈니스 맥락 필요 시 |
| **Controller 메서드** | `@ApiResponse(responseCode, description)` | 주요 에러 응답만 명시 (전부 X) |
| **Path / Query 파라미터** | `@Parameter(description, example)` | public API 문서에 필요한 값만 |
| **DTO** (record / component) | `@Schema(description, example)` | record 와 component 모두 부착 |

구현 규칙:

- 신규 / 수정 Controller 는 `@Tag` 를 붙인다.
- 신규 / 수정 Controller 메서드는 `@Operation` 과 성공 응답 `@ApiResponse` 를 붙인다.
- 비즈니스적으로 중요한 실패 응답은 `@ApiResponse` 로 함께 명시한다.
- 신규 / 수정 Request / Response DTO 는 record 와 각 component 에 `@Schema` 를 붙인다.
- DTO 의 `@Schema` 설명 / 예시 / 길이·범위는 Bean Validation, DB 제약, spec 제약과 어긋나지 않게 작성한다.
- `@PathVariable`, `@RequestParam` 은 API 사용자가 의미를 알기 어려운 경우 `@Parameter` 를 붙인다.
- 프로젝트 응답 envelope 인 `ApiResponse<T>` 와 springdoc 의 `@ApiResponse` 이름이 충돌하면 springdoc 어노테이션 import 를 우선하거나 fully qualified name 을 사용한다.

예시:

```java
@Operation(
    summary = "매장 등록",
    description = "사장이 새 매장을 등록한다. 사업자 인증 + 관리자 승인 필요."
)
@ApiResponse(responseCode = "201", description = "등록 성공")
@ApiResponse(responseCode = "409", description = "이미 등록된 사업자")
@PostMapping
public ResponseEntity<StoreResponse> create(@Valid @RequestBody StoreCreateRequest request) {
    ...
}
```

```java
@Schema(description = "매장 등록 요청")
public record StoreCreateRequest(
    @Schema(description = "매장명", example = "동네빵집")
    @NotBlank
    String name,

    @Schema(description = "주소")
    @NotBlank
    String address
) {}
```
