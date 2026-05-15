# Spec: 별개 계정 모델 및 phone UNIQUE 정책 확정

> 이슈: #31 — https://github.com/MagamPick/magampick-api/issues/31

## 1. Context

마감픽은 회원가입/로그인 기본 흐름 (#15) 으로 이메일·비밀번호 기반 가입과 `OAuthProvider` 인터페이스 + Mock 구현, 그리고 `customer_oauth_accounts` 테이블까지 마련했지만, **소셜 가입자와 이메일 가입자의 계정 모델 관계 (별개 vs 통합) 가 정책으로 확정되지 않은 상태**다. 그 결과 현재 스키마는 통합 친화적 (1:N) 구조인데 정책은 미정인 비정합 상태로 남아 있다.

이번 이슈는 다음을 확정하고 스키마와 정책을 정합 상태로 맞추는 것을 목적으로 한다:

1. **별개 계정 모델 확정** — 카카오 가입자와 이메일 가입자는 별개 `customers` row 를 가진다. 자동 linking 없음. 한국 이커머스 (배민·쿠팡·11번가 등) 표준 패턴이며, 본인인증의 의미를 "이 번호의 실제 소유자 검증" 으로 해석한다.
2. **phone UNIQUE 영구 미적용 확정** — `auth.md §15 Pending Decisions` 의 "phone UNIQUE 재도입" 항목을 폐기한다. 본인인증은 "1번호 1계정" 강제가 아닌 번호 검증 목적.
3. **`customer_oauth_accounts.customer_id` UNIQUE 추가** — 별개 모델은 한 customer 가 한 oauth row 만 갖는다. DB 차원에서 1:1 강제.
4. **소셜 가입 시 이메일 처리 정책** — 카카오 OAuth 의 이메일을 `customers.email` 에 그대로 저장. 충돌 시 `EMAIL_ALREADY_EXISTS` 차단.

카카오 OAuth Mock API 의 실제 구현 (`POST /api/v1/auth/kakao` 등) 은 별도 후속 이슈에서 다룬다. 이번 이슈는 **정책 명세 + 스키마 정합성** 까지가 scope.

## 2. Scope

### In Scope

- **별개 계정 모델 정책 명시** — `auth.md §3` 카카오 부분에 "소셜 가입자와 이메일 가입자는 별개 `customers` row" 명시. 자동 linking 안 함을 명확히
- **phone UNIQUE 영구 미적용 정책 확정** — `auth.md §15 Pending Decisions` 의 "phone UNIQUE 제약 재도입" 항목 제거. `auth.md §8` 의 "시연 시 동일 번호로 customer + seller 둘 다 가입" 사유는 잘못된 근거였음을 반영해 정리
- **`customer_oauth_accounts.customer_id` UNIQUE 제약 추가** — 새 마이그레이션 파일 1개 (`V{timestamp}__add_unique_customer_oauth_accounts_customer.sql`)
- **`customer_oauth_accounts.md` ERD docs 갱신** — UNIQUE 제약 추가 반영
- **소셜 가입 시 이메일 처리 정책 명시** — 카카오 OAuth 의 이메일을 `customers.email` 에 그대로 저장. 카카오 콘솔에서 이메일 필수 동의 전제
- **이메일 충돌 시 정책 명시** — 카카오 이메일이 `customers.email` 에 이미 있으면 `EMAIL_ALREADY_EXISTS` 차단. 자동 linking 안 함

### Out of Scope

- 카카오 OAuth Mock API 구현 (`POST /api/v1/auth/kakao` 등)
- 실제 카카오 API 연동 (Mock → Real 교체)
- OAuth 관련 Entity / Repository / Service / Controller / Mapper / DTO / 테스트 (별도 후속 이슈)
- 카카오 콘솔 설정 (운영 영역)
- 네이버 / 구글 등 다른 소셜 provider 추가
- 통합 계정 모델로의 향후 전환 흐름 (linking endpoint 등)

### 구현 경계

- 정책 명세 (docs) + 스키마 강화 (마이그레이션 1개) + Entity 어노테이션 1줄 (`CustomerOAuthAccount.customer` 의 `@JoinColumn(unique = true)`)
- 이 프로젝트는 **단일 컬럼 UNIQUE 는 Entity 에 표시, 복합 UNIQUE 는 마이그레이션에만** 표시하는 패턴 (`customers.email` 등) — `customer_id` UNIQUE 는 단일 컬럼이라 Entity 에도 반영
- 카카오 OAuth 본격 구현 시 이번 이슈에서 박힌 정책을 그대로 따른다

## 3. User Roles

이번 이슈는 정책 명세 + 스키마 강화라 직접적 API 영향은 없지만, 정책이 적용되는 role 을 명시한다.

### Customer

- **별개 계정 모델 정책 적용** — 카카오로 가입한 customer 와 이메일+비번으로 가입한 customer 는 같은 사람이라도 별개 `customers` row 를 가진다
- **phone UNIQUE 영구 미적용** — 같은 휴대폰 번호로 여러 customer 계정 보유 가능 (한국 이커머스 표준 패턴)
- **이메일 충돌 시 가입 차단** — 카카오 OAuth 의 이메일이 기존 `customers.email` 과 충돌하면 `EMAIL_ALREADY_EXISTS`

### Seller

- **소셜 로그인 미지원** — 사장은 이메일+비번 가입만. 본 이슈 정책 영향 없음
- **phone UNIQUE 영구 미적용** — 한 사람이 여러 사업자 운영 가능한 비즈니스 현실 반영 (같은 phone 으로 여러 seller 계정 가능)

### Admin

- **소셜 로그인 미지원** — 관리자는 이메일+비번 가입만 (셀프 가입 자체 불가, 초대 기반)
- 본 이슈 정책 영향 없음

## 4. API Specification

**해당 없음.** 이번 이슈는 정책 명세 + 스키마 강화 이슈로 API 신규 / 변경 없음.

후속 카카오 OAuth Mock API 구현 이슈에서 다룰 `POST /api/v1/auth/kakao` 는 이번 이슈에서 박힌 정책을 따른다 (Section 6 참조).

## 5. Data Model

### 새 테이블

없음.

### 기존 테이블 변경

- **`customer_oauth_accounts`**: `customer_id` 컬럼에 UNIQUE 제약 추가
  - 기존: `customer_id BIGINT NOT NULL, FK → customers.id`
  - 변경 후: `customer_id BIGINT NOT NULL, FK → customers.id, UNIQUE`
  - 의미: 한 customer 가 최대 1개의 OAuth row 만 보유 (DB 차원 1:1 강제)

### 마이그레이션

새 마이그레이션 파일 1개 추가:

```text
src/main/resources/db/migration/V{YYYYMMDDHHMMSS}__add_unique_customer_oauth_accounts_customer.sql
```

내용:

```sql
ALTER TABLE customer_oauth_accounts
    ADD CONSTRAINT uq_customer_oauth_accounts_customer UNIQUE (customer_id);
```

규칙:
- timestamp 는 마이그레이션 작성 시점 (`YYYYMMDDHHMMSS`) — 기존 `V20260515190000__create_auth_tables.sql` 보다 큰 값
- 기존 마이그레이션 파일 (`V1__init_extensions.sql`, `V20260515190000__create_auth_tables.sql`) 은 수정 금지 (CLAUDE.md)

### ERD

구현 시 다음 ERD 문서를 함께 갱신한다.

- **`docs/erd/tables/customer_oauth_accounts.md`** — `customer_id` UNIQUE 제약 반영
  - 컬럼 표의 `customer_id` 행 제약: `FK` → `FK, UNIQUE`
  - 인덱스 섹션에 추가: `uq_customer_oauth_accounts_customer` UNIQUE (`customer_id`)
  - 제약 섹션에 `uq_customer_oauth_accounts_customer` 추가
  - 상단 한 줄 설명 ("이번 이슈에서는 카카오 Mock 로그인만 다룬다.") 은 도메인 일반 설명으로 정리

- **`docs/erd/overview.md`** — "휴대폰 번호 UNIQUE 미적용" 항목의 사유 정리
  - 기존: "시연 시 동일 번호로 customer + seller 둘 다 가입해야 함. 실제 인증 API 연동 시점에 재검토"
  - 변경 후: 별개 계정 모델 + 사장 다중 사업자 사유 + "영구 미적용" 명시 (auth.md §8 참조)

## 6. Business Logic

이번 이슈는 코드 흐름 구현이 아니라 **정책 명세 + 스키마 강화** 이슈이므로, 정책이 적용될 핵심 흐름을 개념적으로만 정리한다. 실제 구현은 후속 카카오 OAuth Mock API 이슈에서 진행.

### Processing Flow (정책 적용 개념 — 후속 이슈에서 구현)

**카카오 신규 가입 흐름**:

```
1. 클라이언트: 카카오 SDK 로 인가 → kakao access token 획득
2. 서버: POST /api/v1/auth/kakao { kakaoAccessToken }
3. 서버: 카카오 API 호출 → email + provider_user_id 조회
4. customer_oauth_accounts.(KAKAO, provider_user_id) 조회
   ├─ 존재 → 기존 customer 로 로그인 (JWT 발급)
   └─ 미존재 → 신규 가입 흐름:
       a. customers.email 충돌 검사
          ├─ 충돌 → EMAIL_ALREADY_EXISTS (가입 차단)
          └─ 미충돌 → customer row 생성 (password_hash = NULL)
                     + customer_oauth_accounts row 생성
       b. JWT 발급
```

**이메일+비번 가입자가 카카오로도 가입 시도하는 시나리오** (별개 모델의 핵심 케이스):

```
A. 같은 이메일 → EMAIL_ALREADY_EXISTS 차단 (자동 linking 안 함)
B. 다른 이메일 → 신규 카카오 계정 생성 (별개 customer row)
   → 같은 사람이라도 두 customer 계정 보유 (의도된 동작)
```

### Validation Rules

이번 이슈에서 신규 추가하는 validation rule 없음. 후속 이슈에서 사용할 규칙은 #15 `15-signup-login.md` 의 규칙을 그대로 따른다.

### State Transition

해당 없음.

### Error Cases

이번 이슈에서 신규 추가하는 에러 코드 없음. 후속 이슈에서 사용할 에러 코드는 #15 에서 이미 정의됨:

| 상황 | 에러 코드 | HTTP |
|---|---|---|
| 카카오 OAuth 이메일이 `customers.email` 과 충돌 | `EMAIL_ALREADY_EXISTS` | 409 |

### Edge Cases

- **같은 사람이 카카오 → 이메일+비번 순서로 가입 시도** (다른 이메일 사용): 별개 customer row 가 두 개 생성됨 (의도된 동작 — 별개 모델 정의 그대로)
- **같은 이메일로 카카오/이메일 양쪽 가입 시도**: `customers.email` UNIQUE 와 `EMAIL_ALREADY_EXISTS` 차단으로 항상 한 쪽만 성공
- **마이그레이션 실행 시점 기존 데이터**: `customer_oauth_accounts` 테이블은 #15 머지 시 생성되었으나, 카카오 OAuth API 가 아직 구현 안 됨 → row 가 비어 있음. UNIQUE 제약 추가 시 중복 검증 실패 가능성 없음. (#15 의 통합 테스트가 OAuth row 를 만들었다면 테스트 트랜잭션 롤백으로 정리됨 — 운영 DB 동일 가정)
- **카카오 OAuth 이메일을 카카오 측에서 받지 못한 경우**: 카카오 콘솔에서 이메일 필수 동의 설정이 전제 (운영 영역). 후속 이슈에서 가입 차단 정책 등으로 처리 — 이번 이슈 범위 밖.
- **`provider` 가 늘어나는 경우** (네이버 / 구글 등): `customer_id` UNIQUE 정책은 "한 customer 가 한 provider 만" 을 의미. 미래에 한 customer 가 여러 provider 를 linking 하는 통합 모델 전환 시, 이 UNIQUE 제약 제거 한 줄 마이그레이션으로 가능.

### Side Effects

없음.

### Test Cases

이번 이슈는 코드 변경이 없으므로 신규 단위 / 컨트롤러 / 통합 테스트 없음.

스키마 정합성 검증은 다음으로 충족:

- **Flyway 마이그레이션 적용 검증**: 기존 `@SpringBootTest + Testcontainers` 통합 테스트가 빌드 시 새 마이그레이션을 자동 적용 → ALTER TABLE 성공 = 스키마 변경 유효
- **`./gradlew build`** 통과 확인 (spotless + 기존 테스트 통과)

## 7. External Dependencies

이번 이슈에서 외부 API 신규 연동 없음.

후속 카카오 OAuth Mock API 구현 이슈에서 다룰 예정:

- 카카오 개발자 콘솔 — 이메일을 **필수 동의** 항목으로 설정 (운영 영역)
- 환경 변수 `KAKAO_CLIENT_ID`, `KAKAO_CLIENT_SECRET` — Mock → Real 교체 시점에 등록

## 8. Implementation Notes

### 작업 산출물 요약

| 종류 | 파일 | 변경 내용 |
|---|---|---|
| docs | `docs/auth.md` §3 카카오 부분 | 별개 계정 모델 명시 + 매칭 키 `(provider, provider_user_id)` + 이메일 충돌 시 `EMAIL_ALREADY_EXISTS` 정책 |
| docs | `docs/auth.md` §8 휴대폰 본인인증 | phone UNIQUE 미적용 사유 정리 — 잘못된 근거 ("시연 편의") 폐기, 정확한 근거 (별개 모델 / 사장 다중 사업자) 로 교체 |
| docs | `docs/auth.md` §15 Pending Decisions | "phone UNIQUE 제약 재도입" 항목 제거 |
| docs | `docs/erd/tables/customer_oauth_accounts.md` | `customer_id` UNIQUE 제약 반영 (컬럼 / 인덱스 / 제약 섹션) + 상단 도메인 설명 정리 |
| docs | `docs/erd/overview.md` | "휴대폰 번호 UNIQUE 미적용" 사유 정리 + "영구 미적용" 명시 |
| 마이그레이션 | `V{YYYYMMDDHHMMSS}__add_unique_customer_oauth_accounts_customer.sql` | `ALTER TABLE ... ADD CONSTRAINT uq_customer_oauth_accounts_customer UNIQUE (customer_id);` |
| roadmap | `docs/roadmap.md` | 해당 행 상태 / 이슈 번호 갱신 (해당 행 있을 시) |

### docs 수정 상세

#### `docs/auth.md` §3 카카오 OAuth (소셜 로그인)

현재 흐름 설명에 다음 정책 라인을 추가한다:

- **별개 계정 모델** — 소셜 가입자와 이메일 가입자는 같은 사람이라도 별개 `customers` row 를 가진다. 자동 linking 안 함.
- **매칭 키**: `(provider, provider_user_id)` 로 기존 계정 조회. 일치하면 로그인, 미존재면 신규 가입.
- **이메일 처리**: 카카오에서 받은 이메일을 `customers.email` 에 그대로 저장. 카카오 콘솔에서 이메일 필수 동의 설정 전제.
- **이메일 충돌**: 카카오 이메일이 기존 `customers.email` 과 충돌하면 `EMAIL_ALREADY_EXISTS` (409) 로 가입 차단. **자동 linking 안 함** (별개 모델 일관성).
- **소셜 전용 customer**: `password_hash = NULL`. 이메일+비번 로그인 시 비밀번호 불일치 처리 (#15 정의 그대로).

#### `docs/auth.md` §8 휴대폰 본인인증 — DB 설명

기존 텍스트:

> `customers.phone`, `sellers.phone` 은 **UNIQUE 제거** (시연 시 동일 번호로 customer + seller 둘 다 가입해야 하기 때문)

변경 후:

> `customers.phone`, `sellers.phone` 둘 다 UNIQUE 영구 미적용. 사유:
> - **별개 계정 모델** — 한 사람이 카카오 / 이메일+비번으로 각각 customer 계정 보유 가능 (별개 row)
> - **사장 다중 사업자** — 한 사람이 여러 사업자를 운영하는 비즈니스 현실 (같은 phone 으로 여러 seller 계정 자연스러움)
> - **본인인증의 의미** = "번호 소유자 검증" 이지 "1번호 1계정 강제" 가 아님 (한국 이커머스 표준 패턴: 쿠팡, 배민 모두 phone UNIQUE 없음)

#### `docs/auth.md` §15 Pending Decisions

다음 항목 제거:

> - `phone` UNIQUE 제약 재도입 — 출시 시점 결정 (졸업 프로젝트 단계엔 제거)

#### `docs/erd/tables/customer_oauth_accounts.md`

- 상단 한 줄 설명 변경:
  - 기존: "소비자 소셜 계정 연결 테이블. 이번 이슈에서는 카카오 Mock 로그인만 다룬다."
  - 변경 후: "소비자 소셜 계정 연결 테이블. 한 customer 는 최대 1개의 OAuth row 를 가진다 (별개 계정 모델 — auth.md §3)."
- 컬럼 표 `customer_id` 행 제약 컬럼: `FK` → `FK, UNIQUE`
- 인덱스 섹션에 추가: `- uq_customer_oauth_accounts_customer UNIQUE (customer_id)`
- 제약 섹션에 추가:

  ```
  - uq_customer_oauth_accounts_customer
    customer_id 단일 컬럼 UNIQUE (한 customer 1 OAuth row)
  ```

#### `docs/erd/overview.md`

"설계 결정 사항 (전역)" 의 phone 항목 갱신:

- 기존: `**휴대폰 번호 UNIQUE 미적용** (졸업 프로젝트 단계): 시연 시 동일 번호로 customer + seller 둘 다 가입해야 함. 실제 인증 API 연동 시점에 재검토 ([auth.md §8](../auth.md))`
- 변경 후: `**휴대폰 번호 UNIQUE 영구 미적용**: 별개 계정 모델 + 사장 다중 사업자 운영. 본인인증 = 번호 소유자 검증이지 1번호 1계정 강제가 아님 (한국 이커머스 표준). 자세한 사유는 [auth.md §8](../auth.md)`

### Entity 작업

- `CustomerOAuthAccount` Entity 는 #15 에서 이미 생성됨 (`src/main/java/com/magampick/auth/domain/CustomerOAuthAccount.java`)
- 이 프로젝트의 UNIQUE 표현 패턴:
  - **단일 컬럼 UNIQUE** → Entity 의 `@Column(unique = true)` / `@JoinColumn(unique = true)` (`customers.email`, `sellers.email`, `admins.email`, `refresh_tokens.token_hash` 모두 동일)
  - **복합 UNIQUE** → 마이그레이션에만 (`customer_oauth_accounts.(provider, provider_user_id)`)
- `customer_id` UNIQUE 는 단일 컬럼 → Entity 에도 반영:
  - `CustomerOAuthAccount.customer` 의 `@JoinColumn(name = "customer_id", nullable = false)` → `@JoinColumn(name = "customer_id", nullable = false, unique = true)` 로 변경 (1줄)

### 빌드 / 테스트

- 코드 변경 없음 — 신규 테스트 없음
- `./gradlew test` — Flyway 가 Testcontainers 환경에서 새 마이그레이션 자동 적용 → ALTER TABLE 검증
- `./gradlew build` — spotless + 기존 테스트 통과 확인

### 변경하지 않는 부분

- 코드 패키지 구조 (`auth/`, `customer/` 등) — 변경 없음
- 기존 마이그레이션 파일 (`V1__init_extensions.sql`, `V20260515190000__create_auth_tables.sql`) — 수정 금지
- 기존 `#15` spec 파일 (`docs/specs/15-signup-login.md`) — 수정 없음 (별개 결정 / 별개 이슈)
- 전역 컨벤션 (`coding-convention.md`, `api-convention.md`, `test-convention.md`, `commit-convention.md`, `git-workflow.md`) — 수정 없음 (CLAUDE.md /impl docs 수정 범위 가드)
