---
name: impl
description: spec 파일 기반으로 도메인 코드 구현. 옵션 X 순서 (Entity → 마이그레이션 → ERD → Repository → Service+테스트 → DTO+Mapper → Controller+테스트 → spotlessApply → build) 로 한 번에 진행. spec 결정을 기계적으로 코드로 변환 — 임의 결정 X.
---

# /impl — 구현

마감픽 워크플로우 3단계. `/spec` 으로 작성된 spec 파일을 읽고, 옵션 X 순서로 도메인 코드를 한 번에 구현. 빌드 / 테스트 통과까지 확인 후 사용자에게 결과 보고.

> spec 결정 따름. 임의 결정 X. spec 누락 / 막힘 발견 시에만 사용자에게 질문.

## 입력
- `{이슈번호}` — GitHub Issue 번호 (필수, 예: `/impl 12`)
- spec 파일은 자동 탐색: `docs/specs/{N}-*.md`

## 흐름

> **시작 전 — 작업 브랜치 확인 (필수)**
> 현재 브랜치가 `feat/{N}-*` (또는 이슈 type 에 맞는 prefix) 인지 확인.
> - `develop` / `main` 이면 **즉시 중단** — `/spec` 이 작업 브랜치를 만들었어야 함
> - 작업 브랜치가 없으면: `gh issue develop {N} --repo MagamPick/magampick-api --base develop --name "feat/{N}-{슬러그}" --checkout` 로 생성 후 진행

### 1. spec 파일 로드
- `docs/specs/{이슈번호}-*.md` 패턴 탐색
- 매칭 0개 → `/spec {N}` 먼저 호출 안내, 중단
- 매칭 2개 이상 → 사용자에게 선택 받기
- 매칭 1개 → 그대로 사용

### 2. spec 파싱
8섹션 다 읽음:
- 1~3. Context / Scope / User Roles → 컨텍스트
- 4. **API Specification** → Controller / DTO 작성 근거
- 5. **Data Model** → Entity / 마이그레이션 / ERD doc 작성 근거
- 6. **Business Logic** → Service 로직 + Validation / Error / Edge / Test Cases
- 7. **External Dependencies** (해당 시) → 외부 API 어댑터
- 8. **Implementation Notes** (해당 시) → 구현 결정 그대로 적용

### 3. 마이그레이션 V 번호 결정
- **timestamp 형식**: `V{YYYYMMDDHHMMSS}__{설명}.sql` (예: `V20260514153022__create_stores.sql`)
- 현재 시각 기준으로 부여 (worktree 병렬 작업 시 충돌 방지)
- 기존 `V1__init_extensions.sql` 은 그대로 유지 (이미 머지된 것은 절대 수정 X)

### 4. 구현 순서 (옵션 X)

각 단계 끝에서 명시적 검토 X. 한 번에 진행. 막힘 / spec 누락 발견 시에만 사용자에게.

#### 4-1. Entity
- `@Entity` + JPA 어노테이션 ([coding-convention.md](../../../docs/coding-convention.md))
- 위치 = `src/main/java/{package}/{domain}/entity/{Name}.java`
- Enum = `@Enumerated(EnumType.STRING)`, 위치 = `Point` (`org.locationtech.jts.geom.Point` + Hibernate Spatial)
- BaseEntity 있으면 상속 (createdAt / updatedAt / deletedAt)

#### 4-2. 마이그레이션 SQL
- 위치 = `src/main/resources/db/migration/V{YYYYMMDDHHMMSS}__{설명}.sql` (timestamp 형식)
- 컬럼 / 인덱스 / 제약 / FK
- Enum 은 `VARCHAR + CHECK` 제약
- 위치는 `GEOGRAPHY(POINT, 4326)` + GIST 인덱스
- KST timezone

#### 4-3. ERD 상세
- 위치 = `docs/erd/tables/{table}.md`
- 컬럼 / 인덱스 / 제약 / 관계 설명 ([erd/overview.md](../../../docs/erd/overview.md) 따름)

#### 4-4. Repository
- `extends JpaRepository<Entity, Long>`
- 커스텀 쿼리는 `@Query` / 메서드명 규칙
- 기본 CRUD 만이면 테스트 X
- 커스텀 쿼리 있으면 `@DataJpaTest` 테스트 추가 ([test-convention.md](../../../docs/test-convention.md))

#### 4-5. Service + 단위 테스트 (TDD lite)

**먼저 단위 테스트 작성**:
- 위치 = `src/test/java/{package}/{domain}/service/{Name}ServiceTest.java`
- spec Test Cases 의 Service 단위 테스트 케이스 그대로
- **한국어 메서드명** (예: `매장_등록_성공`) + `// given-when-then` 주석
- Mockito + AssertJ

**그 다음 Service 구현**:
- 위치 = `src/main/java/{package}/{domain}/service/{Name}Service.java`
- 비즈니스 로직 = spec Business Logic 따름
- 트랜잭션 경계 = spec Implementation Notes 따름 (기본 = Service 메서드 단위)
- 예외 = `BusinessException` + `BaseErrorCode` ([coding-convention.md](../../../docs/coding-convention.md))

#### 4-6. DTO + MapStruct Mapper
- Request / Response 분리 ([api-convention.md](../../../docs/api-convention.md))
- 검증 = Bean Validation 어노테이션 (spec Validation Rules 따름)
- Mapper = MapStruct (coding-convention §8)
- DTO / Mapper 자체 테스트 X

#### 4-7. Controller + @WebMvcTest (TDD lite)

**먼저 @WebMvcTest 작성**:
- 위치 = `src/test/java/{package}/{domain}/controller/{Name}ControllerTest.java`
- spec Test Cases 의 Controller 테스트 케이스 그대로
- MockMvc + Mockito (Service mock)
- 인증 필요 시 `@WithMockUser` 또는 SecurityContext 설정 ([auth.md](../../../docs/auth.md))

**그 다음 Controller 구현**:
- 위치 = `src/main/java/{package}/{domain}/controller/{Name}Controller.java`
- URL / 메서드 / 상태 코드 = spec API Specification 따름
- `@RestController` + `@RequestMapping` (api-convention URL 룰)
- 응답은 `ApiResponse<T>` 자동 wrap (ResponseBodyAdvice — payload 만 반환)
- 인가 = `@PreAuthorize` 또는 SecurityConfig (auth.md 의 인가 매트릭스)

#### 4-8. spotlessApply
```powershell
./gradlew spotlessApply
```

#### 4-9. build (전체 빌드 + 테스트)
```powershell
./gradlew build
```

빌드 / 테스트 통과 확인. 실패 시:
- **단순 실패** (오타 / import 누락 / 포맷) → 자동 수정 후 재실행 (1~2회)
- **복잡한 실패** (로직 / spec 해석) → 사용자에게 보고 + 결정 받기

#### 4-10. roadmap 갱신

[`docs/roadmap.md`](../../../docs/roadmap.md) 에서 이 기능에 해당하는 행을 찾아:
- 상태 `미착수` → `완료`
- `이슈` 컬럼에 이슈 번호 기록 (예: `#12`)

- 작업 브랜치에서 수정 → 이 변경이 feature PR 에 함께 실려 머지 시 `develop` 에 반영된다 (develop 직접 푸시 금지 우회).
- 해당 기능 행이 roadmap 에 없으면 (scope 신규 추가 등) 적절한 계층에 행 추가.
- 빌드 실패로 중단된 경우 갱신하지 않는다 — `완료` 는 구현 + 빌드 통과 후에만.

### 5. 결과 보고 (사용자 검토 ★)

작업 완료 후 사용자에게 다음 보고:

```markdown
## /impl 완료 — 이슈 #{N}

### 생성 / 수정 파일
- Entity: src/main/java/.../{Name}.java
- 마이그레이션: src/main/resources/db/migration/V{N}__*.sql
- ERD: docs/erd/tables/{table}.md
- Repository: ...
- Service + 테스트: ...
- DTO / Mapper: ...
- Controller + 테스트: ...
- roadmap: docs/roadmap.md (해당 행 `완료` + 이슈 번호)

### 빌드 결과
✅ ./gradlew build 통과
(또는 ❌ 실패 — 원인 + 후속 안내)

### spec 외 결정 (있는 경우만)
- {네이밍 / 컨벤션 선택 등 spec 에 없던 작은 결정}

### 다음 단계
사용자 검토 후 커밋 + PR 진행 (`commit-convention.md` / `git-workflow.md` 따름).
roadmap 행은 이미 `완료` 로 갱신됨 — feature PR 에 함께 포함해 머지.
```

## 중간 질문 — 자연스럽게 (강제 검토 X)

다음 상황에서만 사용자에게 묻기:
- spec 에 명백히 빠진 결정 발견 (예: 외부 API URL / 시크릿 / 환경 변수)
- 빌드 / 테스트가 단순 수정으로 안 되는 실패
- spec 해석이 두 가지 이상 가능한 모호함
- `auth.md` / `docs/erd/tables/` 갱신 시 정책 결정 필요

## 단계별 docs 수정 권한 ([CLAUDE.md](../../../CLAUDE.md))

**수정 OK**:
- `docs/erd/tables/{table}.md` (해당 도메인 ERD 상세)
- `auth.md` (인증 / 인가 정책 결정 필요 시)
- `docs/roadmap.md` (해당 기능 행 상태/이슈 번호 갱신 — 단계 4-10)

**수정 X (별도 이슈로 메모)**:
- api-convention / coding-convention / test-convention / commit-convention / git-workflow

## 에러 처리

| 상황 | 처리 |
|---|---|
| spec 파일 없음 | `/spec {N}` 먼저 호출 안내, 중단 |
| spec 파일 여러 개 매칭 | 사용자에게 선택 받기 |
| 마이그레이션 V 번호 중복 | timestamp 형식이라 거의 없음. 발생 시 +1초로 조정 |
| 빌드 실패 (단순) | 자동 수정 후 재실행 (1~2회) |
| 빌드 실패 (복잡) | 사용자에게 원인 보고 + 결정 받기 |
| 외부 API 키 / 시크릿 부재 | 환경 변수 설정 안내 + 중단 |

## 주의

- **spec 결정 따름** — 임의 변경 X. spec 누락은 사용자에게 질문
- **마이그레이션 V 번호 = timestamp** — 머지된 파일 수정 X (CLAUDE.md)
- **테스트 먼저 작성** (TDD lite) — Service 단위 + Controller @WebMvcTest 다 적용
- **한국어 테스트 메서드명** ([test-convention.md](../../../docs/test-convention.md))
- **결과 보고 = 사용자 검토 시점** — `/impl` 끝나면 사용자가 검토 → OK 면 커밋 / PR (4단계, 스킬 없음)
- **PowerShell 5.1**: 한글 메서드명 / 주석은 UTF-8 (Write tool 기본). Gradle 인코딩 이슈는 빌드 실패 시점에 진단
