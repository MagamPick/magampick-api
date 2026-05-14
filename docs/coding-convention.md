# Coding Convention

Spring Boot 3.5 / Java 21 / JPA / PostgreSQL 기준.

> **코드 포맷팅**(들여쓰기 / 공백 / import 정리 등)은 [Spotless + Google Java Format](../build.gradle) 으로 자동화되어 있으므로 본 문서에서는 다루지 않는다. `./gradlew spotlessApply` 로 일괄 적용.

도메인 용어 매핑은 [`glossary.md`](glossary.md), API 응답 포맷은 [`api-convention.md`](api-convention.md) 참조.

---

## 1. 패키지 구조 — Package by Feature

도메인 단위로 폴더를 나누고, 각 도메인 내부에 레이어를 둔다.

```
com.magampick
├── MagampickApiApplication.java
│
├── global/                          # 비-도메인 공통 (인프라 + 공통 모델)
│   ├── config/                      # JpaAuditingConfig, SwaggerConfig 등
│   ├── exception/                   # BaseErrorCode (interface), CommonErrorCode,
│   │                                #  BusinessException, GlobalExceptionHandler
│   ├── response/                    # ApiResponse, ErrorResponse, ApiResponseAdvice
│   ├── security/                    # Spring Security 설정, JWT
│   └── common/                      # BaseEntity 등 공통 모델
│
├── auth/
│   ├── controller/
│   ├── service/
│   ├── repository/
│   ├── domain/                      # Entity + enum (도메인 모델)
│   ├── dto/                         # Request / Response (record)
│   ├── mapper/                      # MapStruct Mapper
│   └── exception/
│       └── AuthErrorCode.java       # implements BaseErrorCode
├── store/
├── order/
├── product/
├── clearance/                       # 마감 임박 상품 (ClearanceItem)
├── ...
```

- 도메인 폴더 이름은 영문 단수형 소문자 (`store`, `order`, `clearance`)
- 도메인 네이밍은 [`glossary.md`](glossary.md) 의 영문 매핑을 따른다
- 도메인 내부 `domain/` 폴더에는 **Entity + enum + (필요 시) Value Object** 를 함께 둔다 (도메인 모델 응집)

---

## 2. 레이어 책임

| 레이어 | 책임 | 금지 |
|---|---|---|
| **Controller** | HTTP 요청/응답 변환, 입력 검증 트리거 | 비즈니스 로직 |
| **Service** | 비즈니스 로직, 트랜잭션 경계 (`@Transactional`) | HTTP 객체 의존 (`HttpServletRequest` 등) |
| **Repository** | 데이터 접근 (Spring Data JPA) | 비즈니스 판단 |
| **Domain (Entity)** | 도메인 상태와 행동 (비즈니스 메서드) | Spring 의존 |

- 조회 전용 Service 메서드: `@Transactional(readOnly = true)`
- Controller 는 Service 만 호출. Repository 직접 호출 금지

---

## 3. Entity

### 기본 규칙

```java
@Entity
@Table(name = "stores")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Store extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String address;

    @Builder
    private Store(String name, String address) {
        this.name = name;
        this.address = address;
    }

    // 비즈니스 메서드 (Setter 대신)
    public void changeName(String newName) {
        if (newName == null || newName.isBlank()) {
            throw new IllegalArgumentException("name required");
        }
        this.name = newName;
    }
}
```

**규칙:**
- `@Table(name = "...")` **명시 필수** — SQL 예약어 충돌 방지, 인덱스/제약 한 곳에서 관리
  - 테이블명은 **복수형 snake_case** (`stores`, `orders`, `users`, `clearance_items`)
  - `Order`, `User`, `Group` 같은 SQL 예약어 충돌 위험 도메인은 반드시 복수형
- `@NoArgsConstructor(access = PROTECTED)` — JPA 가 요구하는 기본 생성자. 외부에서 호출 금지
- `@Builder` 는 **생성자에만** 부여 (필드 단위 빌더 X). 생성에 필요한 필드만 노출
- `@AllArgsConstructor` 사용 금지 — 생성자 레벨 `@Builder` 패턴이면 불필요하고, `id`/`createdAt` 같은 자동 생성 필드까지 빌더에 노출되어 안티패턴
- `@Setter` 금지 → 상태 변경은 **의미 있는 비즈니스 메서드** 로 표현 (`order.complete()`, `store.changeName(...)`)
- `@ToString`, `@EqualsAndHashCode` 금지 — 양방향 관계 시 무한 루프 / lazy loading 호출 / 민감 정보 노출 위험

### BaseEntity

`createdAt`, `updatedAt` 은 공통 추출.

```java
// global/common/BaseEntity.java
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
```

`MagampickApiApplication` 또는 별도 Config 에 `@EnableJpaAuditing` 추가 필요.

---

## 4. DTO

### 형식: `record` 사용

Java 21 의 `record` 활용. 불변 + 보일러플레이트 제거.

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

- Validation 어노테이션(`@NotBlank`, `@NotNull`, `@Size`)은 component 앞에 부착
- Swagger 어노테이션(`@Schema`)도 동일
- 변환 메서드는 두지 않는다 → MapStruct 사용 (아래 5 참조)

### 네이밍

도메인 prefix 는 항상 유지 (import / 검색 / 리뷰 시 어느 도메인 건지 명확).

| 용도 | 네이밍 | 예 |
|---|---|---|
| 요청 | `{도메인}{액션}Request` | `StoreCreateRequest`, `StoreUpdateRequest` |
| 응답 (요약/목록 아이템) | `{도메인}Response` | `StoreResponse` |
| 응답 (상세) | `{도메인}DetailResponse` | `StoreDetailResponse` |

---

## 5. DTO ↔ Entity 변환 — MapStruct 통일

모든 변환은 **MapStruct Mapper 인터페이스** 로 처리. record 내부에 `toEntity()` / `from()` 메서드 두지 않는다 (일관성).

### Mapper 예시

```java
@Mapper(componentModel = "spring")
public interface StoreMapper {

    Store toEntity(StoreCreateRequest request);

    StoreResponse toResponse(Store store);

    StoreDetailResponse toDetailResponse(Store store);
}
```

### 사용

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreService {

    private final StoreRepository storeRepository;
    private final StoreMapper storeMapper;

    @Transactional
    public StoreResponse create(StoreCreateRequest request) {
        Store store = storeRepository.save(storeMapper.toEntity(request));
        return storeMapper.toResponse(store);
    }
}
```

### 의존성 추가 (`build.gradle`)

아직 추가되지 않은 의존성:

```gradle
dependencies {
    implementation 'org.mapstruct:mapstruct:1.6.3'
    annotationProcessor 'org.mapstruct:mapstruct-processor:1.6.3'
    // Lombok 과 처리 순서 보장
    annotationProcessor 'org.projectlombok:lombok-mapstruct-binding:0.2.0'
}
```

---

## 6. Lombok 정책

| 어노테이션 | 허용 | 비고 |
|---|---|---|
| `@Getter` | ✅ | |
| `@Builder` | ✅ | Entity 에서는 **생성자에만** 부여 |
| `@RequiredArgsConstructor` | ✅ | Service 의존성 주입 표준 |
| `@NoArgsConstructor(access = PROTECTED)` | ✅ | Entity 전용 |
| `@Slf4j` | ✅ | 로깅 |
| `@Data` | ❌ | setter/toString/equals 포함 — 위험 |
| `@Setter` | ❌ | 비즈니스 메서드로 대체 |
| `@ToString` | ❌ | 양방향 관계 무한 루프, lazy loading 호출, 로그 노출 위험 |
| `@EqualsAndHashCode` | ❌ | Entity 는 id 기반 equals 직접 구현 |
| `@AllArgsConstructor` | ❌ | Entity 에서 불필요 — `@Builder` 생성자 패턴이면 의도된 필드만 노출 가능. id/createdAt 등 자동 필드까지 빌더에 새는 안티패턴 |

---

## 7. 예외 처리

### 구조

도메인별로 `ErrorCode` enum 을 분리하고, 공통 인터페이스 `BaseErrorCode` 로 묶는다 (Package by Feature 와 일관).

```
global/exception/
├── BaseErrorCode.java          # interface
├── CommonErrorCode.java        # 입력 검증, 인증, 서버 오류 등 공통
├── BusinessException.java
├── GlobalExceptionHandler.java
└── ErrorResponse.java

{도메인}/exception/
└── {도메인}ErrorCode.java       # implements BaseErrorCode
```

### `BaseErrorCode` 인터페이스

```java
// global/exception/BaseErrorCode.java
public interface BaseErrorCode {
    HttpStatus getStatus();
    String getCode();
    String getMessage();
}
```

### `CommonErrorCode` (공통)

```java
// global/exception/CommonErrorCode.java
@Getter
@RequiredArgsConstructor
public enum CommonErrorCode implements BaseErrorCode {
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "잘못된 입력입니다"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증이 필요합니다"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "FORBIDDEN", "권한이 없습니다"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "서버 오류가 발생했습니다"),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
```

### 도메인별 `ErrorCode` (예: Store)

```java
// store/exception/StoreErrorCode.java
@Getter
@RequiredArgsConstructor
public enum StoreErrorCode implements BaseErrorCode {
    STORE_NOT_FOUND(HttpStatus.NOT_FOUND, "STORE_NOT_FOUND", "매장을 찾을 수 없습니다"),
    STORE_NOT_APPROVED(HttpStatus.FORBIDDEN, "STORE_NOT_APPROVED", "승인되지 않은 매장입니다"),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
```

### `BusinessException`

```java
// global/exception/BusinessException.java
@Getter
public class BusinessException extends RuntimeException {

    private final BaseErrorCode errorCode;

    public BusinessException(BaseErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
```

### 사용

```java
throw new BusinessException(StoreErrorCode.STORE_NOT_FOUND);
throw new BusinessException(CommonErrorCode.INVALID_INPUT);
```

### 통일 응답 envelope — `ApiResponse<T>` / `ErrorResponse`

모든 응답은 `ApiResponse<T>` 로 wrap (성공/실패 일관). 자세한 응답 포맷은 [`api-convention.md`](api-convention.md) 참조.

```java
// global/response/ApiResponse.java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
    boolean success,
    T data,
    ErrorResponse error
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> error(ErrorResponse error) {
        return new ApiResponse<>(false, null, error);
    }
}
```

```java
// global/response/ErrorResponse.java
public record ErrorResponse(
    String code,
    String message,
    OffsetDateTime timestamp,
    @JsonInclude(JsonInclude.Include.NON_NULL) List<FieldError> fieldErrors
) {
    public static ErrorResponse from(BaseErrorCode errorCode) {
        return new ErrorResponse(errorCode.getCode(), errorCode.getMessage(),
            OffsetDateTime.now(), null);
    }

    public static ErrorResponse from(BaseErrorCode errorCode, List<FieldError> fieldErrors) {
        return new ErrorResponse(errorCode.getCode(), errorCode.getMessage(),
            OffsetDateTime.now(), fieldErrors);
    }

    public record FieldError(String field, String message) {}
}
```

### `ApiResponseAdvice` — 성공 응답 자동 wrap

```java
// global/response/ApiResponseAdvice.java
@RestControllerAdvice
public class ApiResponseAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, ...) {
        if (body instanceof ApiResponse<?>) return body;   // 이미 wrap (에러)
        return ApiResponse.success(body);                  // 성공 자동 wrap
    }
}
```

### `GlobalExceptionHandler`

```java
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        BaseErrorCode errorCode = e.getErrorCode();
        log.warn("BusinessException: {}", errorCode.getCode());
        return ResponseEntity
            .status(errorCode.getStatus())
            .body(ApiResponse.error(ErrorResponse.from(errorCode)));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        List<ErrorResponse.FieldError> fieldErrors = e.getBindingResult().getFieldErrors().stream()
            .map(fe -> new ErrorResponse.FieldError(fe.getField(), fe.getDefaultMessage()))
            .toList();
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error(
                ErrorResponse.from(CommonErrorCode.INVALID_INPUT, fieldErrors)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleAll(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error(ErrorResponse.from(CommonErrorCode.INTERNAL_ERROR)));
    }
}
```

응답 포맷(envelope) 구조는 [`api-convention.md`](api-convention.md) 참조.

### 페이지네이션 응답 wrapper — `PageResponse<T>` / `SliceResponse<T>`

용도 구분:
- `PageResponse<T>` — 일반 페이지네이션 (`totalCount` 포함)
- `SliceResponse<T>` — 무한 스크롤 (`totalCount` 없음, count 쿼리 미실행)

```java
// global/response/PageResponse.java
public record PageResponse<T>(
    List<T> content,
    int page,
    int size,
    long totalCount,
    int totalPages,
    boolean hasNext,
    boolean hasPrevious
) {
    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(
            page.getContent(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages(),
            page.hasNext(),
            page.hasPrevious()
        );
    }
}
```

```java
// global/response/SliceResponse.java
public record SliceResponse<T>(
    List<T> content,
    int page,
    int size,
    boolean hasNext
) {
    public static <T> SliceResponse<T> of(Slice<T> slice) {
        return new SliceResponse<>(
            slice.getContent(),
            slice.getNumber(),
            slice.getSize(),
            slice.hasNext()
        );
    }
}
```

Controller 사용:

```java
// 일반 페이지네이션
@GetMapping
public PageResponse<StoreResponse> list(Pageable pageable) {
    Page<StoreResponse> page = storeService.list(pageable);
    return PageResponse.of(page);
}

// 무한 스크롤
@GetMapping("/feed")
public SliceResponse<ClearanceItemResponse> feed(Pageable pageable) {
    Slice<ClearanceItemResponse> slice = clearanceItemService.feed(pageable);
    return SliceResponse.of(slice);
}
```

Service / Repository 는 각각 `Page<T>` / `Slice<T>` 반환. Repository 메서드 반환 타입을 `Slice<T>` 로 두면 Spring Data 가 count 쿼리 자동 생략.

---

## 8. 적용 위한 build.gradle / Application 변경 사항

본 컨벤션을 적용하려면 다음이 필요하다.

```gradle
// build.gradle dependencies

// MapStruct
implementation 'org.mapstruct:mapstruct:1.6.3'
annotationProcessor 'org.mapstruct:mapstruct-processor:1.6.3'
annotationProcessor 'org.projectlombok:lombok-mapstruct-binding:0.2.0'

// springdoc-openapi (Swagger UI) — Spring Boot 3.5 호환 버전
implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.6'

// Flyway (DB 마이그레이션)
implementation 'org.flywaydb:flyway-core'
implementation 'org.flywaydb:flyway-database-postgresql'

// Hibernate Spatial (PostGIS 연동 — GEOGRAPHY/GEOMETRY 타입)
implementation 'org.hibernate.orm:hibernate-spatial'

// JWT (인증 — auth.md 참조)
implementation 'io.jsonwebtoken:jjwt-api:0.12.6'
runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.12.6'
runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.12.6'

// Firebase Admin SDK (FCM 푸시 알림 — product.md Platform 참조)
implementation 'com.google.firebase:firebase-admin:9.4.1'
```

`SwaggerConfig` 구현 및 그룹 분리 등은 [`api-convention.md`](api-convention.md) §12 참조.

```java
// MagampickApiApplication.java
@EnableJpaAuditing
@SpringBootApplication
public class MagampickApiApplication { ... }
```

---

## 9. DB 마이그레이션 — Flyway

스키마 변경은 **모두 Flyway 마이그레이션 파일** 로 관리한다. Hibernate `ddl-auto` 는 **validate 만** 사용 (스키마 자동 변경 금지).

### 파일 위치 / 명명

```
src/main/resources/db/migration/
├── V1__init_extensions.sql        # PostGIS 확장 활성화
├── V2__create_customers.sql
├── V3__create_sellers.sql
├── ...
```

규칙:
- `V{버전}__{설명}.sql` (언더바 두 개)
- 버전은 정수 단조 증가 (`V1`, `V2`, ...) 또는 점 표기 (`V1.1`)
- 설명은 snake_case, 동사형 (`create_`, `add_`, `alter_`)

### 적용 흐름

1. 새 도메인 / 컬럼 추가 시 새 `.sql` 파일 작성 (수동 SQL)
2. 앱 시작 시 Flyway 가 적용 안 된 마이그레이션 자동 실행
3. `flyway_schema_history` 테이블에 기록됨
4. 한 번 실행된 마이그레이션은 다시 실행 안 됨 → **이미 머지된 파일은 절대 수정하지 않는다**. 변경은 새 파일 (`V{n+1}__alter_...`) 로

### 환경별 `ddl-auto`

| 환경 | `ddl-auto` | 비고 |
|---|---|---|
| local | `validate` | Flyway 가 만든 스키마와 Entity 일치 검증 |
| dev | `validate` | 동일 |
| prod | `validate` | 동일 |

Hibernate 가 스키마를 **절대 건드리지 않게** validate 통일. 모든 DDL 은 Flyway 담당.

### Entity 추가 시 흐름

1. Java Entity 작성 (`@Entity ...`)
2. 해당 테이블 / 인덱스 / 제약을 SQL 로 `V{n}__create_xxx.sql` 작성
3. 앱 재시작 → Flyway 마이그레이션 실행 → Hibernate validate 통과 여부 확인
4. ERD 상세 (`docs/erd/tables/{table_name}.md`) 함께 작성
