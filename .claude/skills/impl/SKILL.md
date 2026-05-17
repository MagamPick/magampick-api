---
name: impl
description: spec + convention 기반 도메인 코드 구현. Type 분기 — feat/fix 는 전체 흐름 (spec 로드 → 옵션 X 순서 → 빌드 → 머지), refactor 는 spec 없이 해당 단계만, docs/chore 는 파일 직접 편집 → 빌드 sanity check → 머지. spec = 정책 + API 계약 + 도메인 특수 결정 / convention = mechanical 표준. spec 결정은 따르고, spec 침묵은 convention 에서 가져온다 — 둘 다 침묵할 때만 사용자에게 질문.
---

# /impl — 구현

마감픽 워크플로우 3단계. `/spec` 으로 작성된 spec 파일 + 관련 convention 문서를 함께 읽고, 옵션 X 순서로 도메인 코드를 한 번에 구현. 빌드 / 테스트 통과까지 확인 후 사용자에게 결과 보고.

> spec 결정 따름. spec 에 없는 mechanical detail 은 convention 문서 (`coding-convention` / `api-convention` / `test-convention` / `auth.md` / `erd/overview`) 가 single source. spec / convention 둘 다 침묵하는 결정이 필요할 때만 사용자에게 질문.

## 입력
- `{이슈번호}` — GitHub Issue 번호 (필수, 예: `/impl 12`)
- spec 파일은 자동 탐색: `docs/specs/{N}-*.md`

## 흐름

> **시작 전 — 작업 위치 확인 (필수)**
> `/impl` 은 이슈 #{N} 의 브랜치가 attach 된 **슬롯 안에서** 실행되어야 한다 (`/issue` 가 attach 한 `../magampick-api-wtX`).
> - 현재 브랜치가 `<type>/{N}-*` (이슈 type prefix — `feat` / `fix` / `refactor` / `docs` / `chore`) 이면 → 진행
> - `develop` / `main` (= 메인 디렉터리) 이면 **즉시 중단** — 이슈 #{N} 의 브랜치가 어느 슬롯에 attach 돼 있는지 (`git worktree list` 로 확인) 안내하고 "그 디렉터리에서 에이전트 띄워 `/impl {N}` 재실행" 안내
> - 어느 슬롯에도 attach 안 돼 있으면 → `/issue` 또는 `/spec {N}` 먼저 안내, 중단
> - (Claude Code 한정 편의: relaunch 대신 `EnterWorktree` 로 슬롯 진입 후 진행해도 됨. Codex 엔 없음)
> - 슬롯 운영 룰 자세히는 [AGENTS.md §"병렬 운영"](../../../AGENTS.md) 참조

> **Type 분기 (필수)** — 이슈 type 라벨에 따라 적용 단계가 다르다. Type 확인: `gh issue view {N} --json labels` 또는 `git branch --show-current` 의 `<type>/...` prefix.
>
> | Step | feat / fix | refactor | docs / chore |
> |---|---|---|---|
> | §1 spec 파일 로드 | ✓ | ✓ (있으면) | skip |
> | §2 spec 파싱 | ✓ | ✓ (있으면) | skip |
> | §3 마이그레이션 V 번호 | 필요 시 | 필요 시 | skip |
> | §4-1 ~ §4-7 (Entity ~ Controller+테스트) | 전체 | 해당 단계만 | skip |
> | §4-8 통합 테스트 | 핵심 흐름 시 | 해당 시 | skip |
> | §4-9 spotlessApply | ✓ | ✓ | skip (코드 변경 없음) |
> | §4-10 build (sanity check) | ✓ | ✓ | ✓ |
> | §4-11 roadmap 갱신 | ✓ | 해당 시 | 해당 시 |
> | §5 결과 보고 + 머지 | ✓ | ✓ | ✓ |
>
> **`docs` / `chore` 흐름**: spec / 코드 단계 모두 skip. 작업 = 이슈 `Changes` 섹션에 명시된 파일 직접 편집 → §4-10 build (sanity check) → §4-11 roadmap (해당 시) → §5 머지.
> **`refactor` 흐름**: 큰 정책 결정 / API 변경이 있으면 `/spec {N}` 명시 호출 후 진행. 그 외엔 spec 없이 §4-1~§4-7 중 해당 단계만.

### 1. spec 파일 로드

> 적용: `feat` / `fix` (필수), `refactor` (있으면). `docs` / `chore` 는 **skip → §4-10 으로**.

- `docs/specs/{이슈번호}-*.md` 패턴 탐색
- 매칭 0개 → `feat` / `fix` 면 `/spec {N}` 먼저 호출 안내, 중단. `refactor` 면 spec 없이 §4 로 진행
- 매칭 2개 이상 → 사용자에게 선택 받기
- 매칭 1개 → 그대로 사용

### 2. spec 파싱
7섹션 다 읽음:
- 1~2. Context / Scope → 컨텍스트
- 3. **API Specification** → Controller / DTO 작성 근거 (필드 / 제약 / 에러 표 — Swagger 어노테이션 본문은 spec 에 없으니 api-convention §12 룰로 부착)
- 4. **Data Model** → Entity / 마이그레이션 / ERD doc 작성 근거
- 5. **Business Logic** → Service 로직 + Validation / Error / Edge (표준 Processing Flow / 표준 Test Cases 는 spec 에 없으니 convention + 표준 흐름으로 도출)
- 6. **External Dependencies** (해당 시) → 외부 API 어댑터
- 7. **Implementation Notes** (해당 시) → convention 밖 결정만 들어 있음 — 그대로 적용

> **spec 침묵 → convention single source**: spec 은 정책 / API 계약 / 도메인 특수 동작만 담는다 (`/spec` SKILL §4 §0 "Don't write" 리스트). mechanical detail 은 다음 convention 문서에서 가져온다 — 추측 X, 일관 적용:
> - Swagger / OpenAPI 어노테이션 부착 → [`api-convention.md`](../../../docs/api-convention.md) §12
> - 패키지 / 레이어 / `@Transactional` 위치 / 예외 / 로깅 / MapStruct → [`coding-convention.md`](../../../docs/coding-convention.md) §1~3, §7, §8, §10
> - 테스트 종류 / 강도 / Fixture / 한국어 메서드명 → [`test-convention.md`](../../../docs/test-convention.md)
> - 인증 / 인가 / 본인 리소스 접근 → [`auth.md`](../../../docs/auth.md)
> - 마이그레이션 / Enum CHECK / Point / KST → [`erd/overview.md`](../../../docs/erd/overview.md)
> - 표준 Processing Flow (JWT 추출 → repository.findById → 404 → dirty checking → Mapper) 는 별도 명시 없이 표준대로

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

#### 4-5. Service + 단위 테스트 (동시 작성)

**단위 테스트와 Service 를 동시에 작성**. AI 컨텍스트에서 둘은 같은 generation 단계에 만들어지므로 엄격한 "테스트 먼저 → 코드" 순서는 의미가 적다. 핵심은 *함께 있다* 는 것.

**단위 테스트**:
- 위치 = `src/test/java/{package}/{domain}/service/{Name}ServiceTest.java`
- spec Test Cases 의 Service 단위 테스트 케이스 그대로
- **한국어 메서드명** (예: `매장_등록_성공`) + `// given-when-then` 주석
- Mockito + AssertJ

**Service 구현**:
- 위치 = `src/main/java/{package}/{domain}/service/{Name}Service.java`
- 비즈니스 로직 = spec Business Logic 따름
- 트랜잭션 경계 = spec Implementation Notes 따름 (기본 = Service 메서드 단위)
- 예외 = `BusinessException` + `BaseErrorCode` ([coding-convention.md](../../../docs/coding-convention.md))

#### 4-6. DTO + MapStruct Mapper
- Request / Response 분리 ([api-convention.md](../../../docs/api-convention.md))
- 검증 = Bean Validation 어노테이션 (spec Validation Rules 따름)
- Mapper = MapStruct (coding-convention §8)
- DTO / Mapper 자체 테스트 X

#### 4-7. Controller + @WebMvcTest (동시 작성)

**@WebMvcTest 와 Controller 를 동시에 작성**. 4-5 와 동일한 이유 — 작성 순서보다 "함께 있는지" 가 핵심.

**@WebMvcTest**:
- 위치 = `src/test/java/{package}/{domain}/controller/{Name}ControllerTest.java`
- spec Test Cases 의 Controller 테스트 케이스 그대로
- MockMvc + Mockito (Service mock)
- 인증 필요 시 `@WithMockUser` 또는 SecurityContext 설정 ([auth.md](../../../docs/auth.md))

**Controller 구현**:
- 위치 = `src/main/java/{package}/{domain}/controller/{Name}Controller.java`
- URL / 메서드 / 상태 코드 = spec API Specification 따름
- `@RestController` + `@RequestMapping` (api-convention URL 룰)
- 응답은 `ApiResponse<T>` 자동 wrap (ResponseBodyAdvice — payload 만 반환)
- Springdoc OpenAPI 어노테이션 부착 (api-convention Swagger / OpenAPI 룰)
  - Controller class: `@Tag`
  - Controller method: `@Operation`, 성공 / 주요 실패 `@ApiResponse`
  - DTO record / component: `@Schema`
  - Path / Query parameter: 필요 시 `@Parameter`
- 인가 = `@PreAuthorize` 또는 SecurityConfig (auth.md 의 인가 매트릭스)

#### 4-8. 통합 테스트 (핵심 흐름인 경우만)

spec 의 Business Logic / Test Cases 가 **핵심 비즈니스 흐름 (회원가입 / 주문 / 결제 / 환불)** 에 해당하면 통합 테스트 작성. 그 외 부수 기능은 이 단계 skip.

- 위치 = `src/test/java/{package}/{domain}/{Name}IntegrationTest.java`
- `@SpringBootTest @AutoConfigureMockMvc @Transactional` + `extends PostgresTestBase` ([test-convention.md §8 / §10](../../../docs/test-convention.md))
- 실제 DB (Testcontainers PostGIS) + 실제 Service ↔ Repository 협업
- 검증 시나리오 = spec Test Cases 의 통합 시나리오 그대로
- **목적**: 단위/슬라이스 테스트의 mock 가정이 어긋나는 부분 (트랜잭션 경계 / FK / 보안 필터 / Validation 흐름) 을 여기서 드러냄. AI 의 자기참조 검증 위험 차단
- 핵심 흐름이 아니면 skip — `test-convention.md §2` 의 🟢 선택 항목들 (Repository 커스텀 쿼리 / E2E) 은 명시 요청 시만

#### 4-9. spotlessApply
```powershell
./gradlew spotlessApply
```

#### 4-10. build (전체 빌드 + 테스트)
```powershell
./gradlew build
```

빌드 / 테스트 통과 확인. 실패 시:
- **단순 실패** (오타 / import 누락 / 포맷) → 자동 수정 후 재실행 (1~2회)
- **복잡한 실패** (로직 / spec 해석) → 사용자에게 보고 + 결정 받기

#### 4-11. roadmap 갱신

[`docs/roadmap.md`](../../../docs/roadmap.md) 에서 이 기능에 해당하는 행을 찾아:
- 상태 `미착수` → `완료`
- `이슈` 컬럼에 이슈 번호 기록 (예: `#12`)

- 작업 브랜치에서 수정 → 이 변경이 feature PR 에 함께 실려 머지 시 `develop` 에 반영된다 (develop 직접 푸시 금지 우회).
- 해당 기능 행이 roadmap 에 없으면 (scope 신규 추가 등) 적절한 계층에 행 추가.
- 빌드 실패로 중단된 경우 갱신하지 않는다 — `완료` 는 구현 + 빌드 통과 후에만.

### 5. 결과 보고 + 머지까지 진행 (사용자 검토 ★)

빌드 통과 후 사용자에게 다음 보고:

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

### spec + convention 밖 결정 (있는 경우만)
- {spec 도 convention 도 다루지 않아 만든 결정만. convention 따라 자동 적용된 mechanical detail (패키지 경로 / @Transactional 위치 / Swagger 어노테이션 / MapStruct / 로그 포맷 등) 은 적지 않음}
```

보고 직후 머지까지 같은 세션에서 끝낸다 ([`AGENTS.md` 워크플로우 4단계](../../../AGENTS.md) / [`git-workflow.md §4`](../../../docs/git-workflow.md)):

1. **커밋 메시지 검토** — `<emoji> <type>: <subject>` **한 줄만** ([`commit-convention.md` §2](../../../docs/commit-convention.md) — body / footer 사용 안 함). 작성한 커밋 메시지 + 커밋 파일 목록을 사용자에게 보여주고 OK 받기 ([`AGENTS.md` Git 섹션](../../../AGENTS.md)). `commit-msg` hook 이 body 있는 커밋을 reject 하므로 우회 / `--no-verify` 금지
2. **커밋 + 푸시**
3. **PR 본문 검토** — `gh pr create` 호출 전 제목 / 본문을 사용자에게 보여주고 OK 받기. **이 시점이 머지까지 위임받는 동의 시점**
4. **PR 생성** — `gh pr create --base develop ...`
5. **CI watch** — `gh pr checks {N} --repo MagamPick/magampick-api --watch` 를 background 로 실행. 다른 폴링 / sleep 금지
6. **자동 머지** — CI green 시 즉시 `gh pr merge {N} --merge --delete-branch`. 사용자 추가 확인 없이 진행 (CI = 머지 게이트, `git-workflow.md §4`). 머지 결과는 `gh pr view ... --json state,mergedAt,mergeCommit` 으로 즉시 검증
7. **슬롯 정리 + develop pull**
   ```sh
   git fetch --prune
   git switch --detach origin/develop          # 현재 슬롯을 빈 상태로
   git branch -D {type}/{N}-{슬러그}            # 로컬 브랜치 삭제
   git -C "{메인 디렉터리 절대경로}" pull        # 메인의 develop 최신화
   ```
   원격 브랜치는 `--delete-branch` 로 이미 삭제됨.
8. **사이클 완료 보고** — PR URL / merge commit / 다음 단계 안내

CI red 인 경우: watch 결과의 실패 원인 + 다음 액션 후보 (수정 후 추가 커밋 vs 롤백 vs 상의) 를 보고 후 사용자 결정 대기. 임의로 강제 머지 / 머지 시도 X.

`/spec` 을 건너뛰고 `/impl` 만 진행한 경우 (단순 docs 메타 작업 등) 도 위 흐름은 동일.

## 중간 질문 — 자연스럽게 (강제 검토 X)

다음 상황에서만 사용자에게 묻기:
- **spec + convention 둘 다 침묵하는 결정 발견** — mechanical 이면 convention 에서 가져오고, 정책성이면 사용자에게. 예: 외부 API URL / 시크릿 / 환경 변수, spec 에 없는 도메인 특수 분기
- 빌드 / 테스트가 단순 수정으로 안 되는 실패
- spec 해석이 두 가지 이상 가능한 모호함
- `auth.md` / `docs/erd/tables/` 갱신 시 정책 결정 필요

## 단계별 docs 수정 권한 ([CLAUDE.md](../../../CLAUDE.md))

**수정 OK**:
- `docs/erd/tables/{table}.md` (해당 도메인 ERD 상세)
- `auth.md` (인증 / 인가 정책 결정 필요 시)
- `docs/roadmap.md` (해당 기능 행 상태/이슈 번호 갱신 — 단계 4-11)

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

- **worktree 안에서 실행** — 시작 가드 필수. 주 디렉터리(`develop`/`main`)면 중단하고 worktree 로 안내
- **spec 결정 따름 + convention 위임** — spec 결정은 그대로 적용. spec 침묵은 convention 문서에서 가져온다 (mechanical 표준). 둘 다 침묵하면 사용자에게 질문 (§2 spec 파싱의 convention 매핑 표 참조)
- **마이그레이션 V 번호 = timestamp** — 머지된 파일 수정 X (CLAUDE.md)
- **테스트 동시 작성** — Service 단위 + Controller @WebMvcTest 항상. 핵심 흐름 (가입 / 주문 / 결제 / 환불) 은 통합 테스트도 (단계 4-8). 작성 순서보다 *함께 있는지* 가 중요 (AI 자기참조 검증 위험 보완)
- **한국어 테스트 메서드명** ([test-convention.md](../../../docs/test-convention.md))
- **결과 보고 = 사용자 검토 시점** — `/impl` 끝나면 사용자가 검토 → OK 면 커밋 / PR (4단계, 스킬 없음)
- **PowerShell 5.1**: 한글 메서드명 / 주석은 UTF-8 (Write tool 기본). Gradle 인코딩 이슈는 빌드 실패 시점에 진단
