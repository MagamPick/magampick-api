---
name: impl
description: 이슈 + plan mode 합의 + convention 기반 도메인 코드 구현. Type 분기 — feat/fix 는 plan mode → 옵션 X 순서 → 빌드 → 머지, refactor 는 plan mode → 해당 단계만, docs/chore 는 파일 직접 편집 → 빌드 sanity check → 머지. 이슈 본문 + plan 합의 = 정책 / API 계약 / 도메인 특수 결정 / convention = mechanical 표준. spec 파일이 있으면 함께 읽지만 자동 흐름엔 옵트인. plan / convention 둘 다 침묵할 때만 사용자에게 질문.
---

# /impl — 구현

마감픽 워크플로우 2~3단계 (plan mode → 구현 → 머지). 이슈 본문 + plan mode 합의 + 관련 convention 문서를 함께 읽고, 옵션 X 순서로 도메인 코드를 한 번에 구현. 빌드 / 테스트 통과까지 확인 후 사용자에게 결과 보고 → 같은 세션에서 머지까지.

> 이슈 본문 + plan 합의 따름. mechanical detail 은 convention 문서 (`coding-convention` / `api-convention` / `test-convention` / `auth.md` / `erd/overview`) 가 single source. plan / convention 둘 다 침묵하는 결정이 필요할 때만 사용자에게 질문.
>
> **spec 파일** (`docs/specs/{N}-*.md`) 은 옵트인 — 있으면 함께 읽고 따른다. 자동 흐름에서 부재가 차단 조건 아님. handoff 가 필요한 경우 (다른 세션 / 모델 / 외주) 만 `/spec` 을 먼저 명시 호출.

## 입력
- `{이슈번호}` — GitHub Issue 번호 (필수, 예: `/impl 12`)
- spec 파일은 자동 탐색 (있으면): `docs/specs/{N}-*.md`

## 흐름

> **시작 전 — 작업 위치 확인 (필수)**
> `/impl` 은 이슈 #{N} 의 브랜치가 attach 된 **슬롯 안에서** 실행되어야 한다 (`/issue` 가 attach 한 `../magampick-api-wtX`).
> - 현재 브랜치가 `<type>/{N}-*` (이슈 type prefix — `feat` / `fix` / `refactor` / `docs` / `chore`) 이면 → 진행
> - `develop` / `main` (= 메인 디렉터리) 이면 **즉시 중단** — 이슈 #{N} 의 브랜치가 어느 슬롯에 attach 돼 있는지 (`git worktree list` 로 확인) 안내하고 "그 디렉터리에서 에이전트 띄워 `/impl {N}` 재실행" 안내
> - 어느 슬롯에도 attach 안 돼 있으면 → `/issue` 먼저 안내 (또는 fallback: 빈 슬롯에 브랜치 직접 attach 후 재실행), 중단
> - (Claude Code 한정 편의: relaunch 대신 `EnterWorktree` 로 슬롯 진입 후 진행해도 됨. Codex 엔 없음)
> - 슬롯 운영 룰 자세히는 [AGENTS.md §"병렬 운영"](../../../AGENTS.md) 참조

> **Type 분기 (필수)** — 이슈 type 라벨에 따라 적용 단계가 다르다. Type 확인: `gh issue view {N} --json labels` 또는 `git branch --show-current` 의 `<type>/...` prefix.
>
> | Step | feat / fix | refactor | docs / chore |
> |---|---|---|---|
> | §0 plan mode 진입 + 합의 | ✓ | ✓ | ✓ (간단 plan — 편집 파일 목록 확인) |
> | §1 이슈 + (옵션) spec 로드 | ✓ | ✓ | ✓ |
> | §2 입력 파싱 (이슈 / spec / convention) | ✓ | ✓ | skip (코드 입력 없음) |
> | §3 마이그레이션 V 번호 | 필요 시 | 필요 시 | skip |
> | §4-1 ~ §4-7 (Entity ~ Controller+테스트) | 전체 | 해당 단계만 | skip |
> | §4-8 통합 테스트 | 핵심 흐름 시 | 해당 시 | skip |
> | §4-9 spotlessApply | ✓ | ✓ | skip (코드 변경 없음) |
> | §4-10 build (sanity check) | ✓ | ✓ | ✓ |
> | §4-11 외부 모델 리뷰 | ✓ | ✓ | skip (명시 요청 시만) |
> | §4-12 roadmap 갱신 | ✓ | 해당 시 | 해당 시 |
> | §5 결과 보고 + 머지 | ✓ | ✓ | ✓ |
>
> **`docs` / `chore` 흐름**: 코드 단계 모두 skip. 작업 = §0 간단 plan (편집 파일 / 변경 범위 확인) → 이슈 `Changes` 섹션에 명시된 파일 직접 편집 → §4-10 build (sanity check) → §4-12 roadmap (해당 시) → §5 머지. (§4-11 외부 리뷰는 명시 요청 시만)
> **`refactor` 흐름**: §0 plan mode 에서 정책 결정 / API 변경 영향 합의. 큰 handoff 가 필요하면 사용자가 `/spec {N}` 을 명시 호출 후 진행 (드문 케이스).

### 0. plan mode 진입 + 합의 (모든 type 필수)

> 첫 코드 / 파일 편집 **전**에 plan mode 로 들어가서 사용자와 합의한다. plan mode 가 spec 의 "구현 전 결정 검토" 역할을 in-session 휘발성으로 수행한다.

**입력 수집** (plan mode 안에서):
1. **이슈 본문 로드** — `gh issue view {N} --repo MagamPick/magampick-api --json title,body,labels` 로 가져온다. 이슈의 Context / Scope / 핵심 정책 결정 / Business Logic 큰 그림 / (docs 면) Changes 가 plan 의 1차 입력.
2. **spec 파일 탐색 (옵션)** — `docs/specs/{N}-*.md` 매칭이 있으면 함께 읽고 plan 에 반영. 없으면 그대로 진행. 부재는 차단 조건 아님.
3. **convention 사전 점검** — feat/fix 면 변경 영향 받는 convention 섹션을 머릿속에 두고 plan 짠다.

**plan 에 포함할 내용** (type 에 따라 적절히):
- 영향 받는 엔드포인트 / 엔티티 / 마이그레이션 목록
- 영향도 높은 결정 — **이슈에 명시되지 않았거나 모호한 부분만** 옵션으로 명시:
  - 다중성 / 카디널리티 (1:1 vs 1:N, 단일 vs 다중 선택)
  - 권한 분기 (role 별 차이)
  - 인덱스 / 유니크 영향
  - 마이그레이션 영향 (기존 컬럼 NOT NULL 추가 등)
  - Enum 후보 / 상태값 누락
  - 외부 시스템 의존 (Mock vs 실제 연동)
- 적용 단계 (Type 분기 표 기준 — 어디부터 어디까지)
- (docs/chore) 편집 대상 파일 목록

**합의 룰**:
- **영향도 높은 결정 / `features.md` / `policy.md` 와 충돌하는 가정**은 임의 가정 금지. 옵션을 명시적으로 제시 (Claude Code 는 AskUserQuestion, Codex 는 native 프롬프트 — 에이전트별 적합한 방식)
- 이슈 본문 / spec 에 이미 명확히 박힌 결정은 plan 에 다시 적지 않는다 (중복 검토 피하기)
- plan 합의 = "이렇게 진행" 동의. plan exit 후 §1~§5 진행

### 1. 이슈 + (옵션) spec 로드

§0 에서 plan 합의 시 이미 로드. 이 단계는 재확인:
- 이슈 본문 (Context / Scope / 정책 결정 / Business Logic) — 항상 1차 source
- spec 파일 (`docs/specs/{이슈번호}-*.md`) — 매칭 시 함께 사용. 매칭 2개 이상이면 사용자에게 선택 받기. 매칭 0개는 정상 (옵트인이므로 차단 X)

### 2. 입력 파싱 (이슈 / spec / convention)

> 적용: `feat` / `fix` / `refactor`. `docs` / `chore` 는 **skip → §4-10 으로**.

이슈 본문 + spec (있으면) 을 다음과 같이 매핑:
- 이슈 Context / Scope → 컨텍스트
- 이슈 핵심 정책 결정 + plan 합의 → 권한 / 다중성 / 분기 결정의 source of truth
- 이슈 Business Logic 큰 그림 → Service 로직 골격
- spec 이 있고 § 3 API Specification / §4 Data Model / §5 Business Logic 이 채워져 있으면 → Controller / DTO / Entity / 마이그레이션 작성 근거 (필드 / 제약 / 에러 표 — Swagger 어노테이션 본문은 spec 에 없으니 api-convention §12 룰로 부착)
- spec §7 Implementation Notes (있으면) → convention 밖 결정만 들어 있음 — 그대로 적용

> **이슈 / spec 침묵 → convention single source**: mechanical detail 은 다음 convention 문서에서 가져온다 — 추측 X, 일관 적용:
> - Swagger / OpenAPI 어노테이션 부착 → [`api-convention.md`](../../../docs/api-convention.md) §12
> - 패키지 / 레이어 / `@Transactional` 위치 / 예외 / 로깅 / MapStruct → [`coding-convention.md`](../../../docs/coding-convention.md) §1~4, §8, §9, §11
> - 테스트 종류 / 강도 / Fixture / 한국어 메서드명 → [`test-convention.md`](../../../docs/test-convention.md)
> - 인증 / 인가 / 본인 리소스 접근 → [`auth.md`](../../../docs/auth.md)
> - 마이그레이션 / Enum CHECK / Point / KST → [`erd/overview.md`](../../../docs/erd/overview.md)
> - 표준 Processing Flow (JWT 추출 → repository.findById → 404 → dirty checking → Mapper) 는 별도 명시 없이 표준대로
>
> 이슈 / spec / convention 셋 다 침묵하는 결정만 사용자에게 질문 (§0 에서 이미 합의됐어야 정상).

### 3. 마이그레이션 V 번호 결정
- **timestamp 형식**: `V{YYYYMMDDHHMMSS}__{설명}.sql` (예: `V20260514153022__create_stores.sql`)
- 현재 시각 기준으로 부여 (worktree 병렬 작업 시 충돌 방지)
- 기존 `V1__init_extensions.sql` 은 그대로 유지 (이미 머지된 것은 절대 수정 X)

### 4. 구현 순서 (옵션 X)

각 단계 끝에서 명시적 검토 X. 한 번에 진행. §0 plan 합의 범위 안에서만 — 벗어나는 결정 필요 시 사용자에게.

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
- 이슈 Business Logic + plan 합의 + (spec 있으면) Test Cases 에서 표준 케이스 도출
- **한국어 메서드명** (예: `매장_등록_성공`) + `// given-when-then` 주석
- Mockito + AssertJ

**Service 구현**:
- 위치 = `src/main/java/{package}/{domain}/service/{Name}Service.java`
- 비즈니스 로직 = 이슈 Business Logic + plan 합의 (+ spec 있으면 Business Logic) 따름
- 트랜잭션 경계 = plan 합의 (+ spec Implementation Notes 있으면) 따름. 기본 = Service 메서드 단위
- 예외 = `BusinessException` + `BaseErrorCode` ([coding-convention.md](../../../docs/coding-convention.md))

#### 4-6. DTO + MapStruct Mapper
- Request / Response 분리 ([api-convention.md](../../../docs/api-convention.md))
- 검증 = Bean Validation 어노테이션 (이슈 / plan / (있으면) spec Validation Rules 따름)
- Mapper = MapStruct (coding-convention §9)
- DTO / Mapper 자체 테스트 X

#### 4-7. Controller + @WebMvcTest (동시 작성)

**@WebMvcTest 와 Controller 를 동시에 작성**. 4-5 와 동일한 이유 — 작성 순서보다 "함께 있는지" 가 핵심.

**@WebMvcTest**:
- 위치 = `src/test/java/{package}/{domain}/controller/{Name}ControllerTest.java`
- 이슈 API 표 + plan 합의 + (spec 있으면) Test Cases 에서 표준 Controller 테스트 케이스 도출
- MockMvc + Mockito (Service mock)
- 인증 필요 시 `@WithMockUser` 또는 SecurityContext 설정 ([auth.md](../../../docs/auth.md))

**Controller 구현**:
- 위치 = `src/main/java/{package}/{domain}/controller/{Name}Controller.java`
- URL / 메서드 / 상태 코드 = 이슈 + plan 합의 (+ spec 있으면 API Specification) 따름
- `@RestController` + `@RequestMapping` (api-convention URL 룰)
- 응답은 `ApiResponse<T>` 자동 wrap (ResponseBodyAdvice — payload 만 반환)
- Springdoc OpenAPI 어노테이션 부착 (api-convention Swagger / OpenAPI 룰)
  - Controller class: `@Tag`
  - Controller method: `@Operation`, 성공 / 주요 실패 `@ApiResponse`
  - DTO record / component: `@Schema`
  - Path / Query parameter: 필요 시 `@Parameter`
- 인가 = `@PreAuthorize` 또는 SecurityConfig (auth.md 의 인가 매트릭스)

#### 4-8. 통합 테스트 (핵심 흐름인 경우만)

이슈 / plan / (있으면) spec Business Logic 이 **핵심 비즈니스 흐름 (회원가입 / 주문 / 결제 / 환불)** 에 해당하면 통합 테스트 작성. 그 외 부수 기능은 이 단계 skip.

- 위치 = `src/test/java/{package}/{domain}/{Name}IntegrationTest.java`
- `@SpringBootTest @AutoConfigureMockMvc @Transactional` + `extends PostgresTestBase` ([test-convention.md §8 / §10](../../../docs/test-convention.md))
- 실제 DB (Testcontainers PostGIS) + 실제 Service ↔ Repository 협업
- 검증 시나리오 = 이슈 Business Logic + plan + (있으면) spec Test Cases 의 통합 시나리오 도출
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
- **복잡한 실패** (로직 / 이슈·spec 해석) → 사용자에게 보고 + 결정 받기

#### 4-11. 외부 모델 리뷰 (feat / fix / refactor)

> 적용: `feat` / `fix` / `refactor`. `docs` / `chore` 는 skip (명시 요청 시만 진행).

빌드 통과 후, 커밋 전에 외부 모델로 코드 리뷰를 받는다. 구현 모델의 blindspot 우회 + 컨벤션 / 객체지향 / Spring Boot 관행 비판적 검토 목적.

**모델 선택**:
- 디폴트: **Codex 5.5 medium**
- 작업 시작 시 사용자가 토큰 잔량 / 작업 중요도 보고 결정 가능 (Codex 5.5 high / Opus 4.7 / Sonnet 다른 인스턴스 등). 구현 모델과 다른 family 권장 (blindspot 다름)

**호출 위치**: 현재 작업 슬롯 (read-only — 다른 worktree 안 만들어도 됨)

**호출 방식**: 에이전트별 적합한 방식
- Claude Code: Agent tool 또는 headless `claude -p ...`
- Codex: 새 세션 호출
- 기타: 등가 방식

**리뷰 prompt** — 다음 8개 카테고리를 모두 짚어달라 (컨벤션 ticked off 라도 case 적합성 비판):

1. **의도 정합성** — 이슈 본문 / plan 합의와 부합 / scope 안 / over-engineering 없음
2. **캡슐화 / OO** — Tell-Don't-Ask / predicate 명명 (state vs capability) / SRP / 생성 패턴 (builder vs factory) 일관
3. **Spring Boot 관행** — `@Transactional` 경계 / 예외 핸들링 / multipart 설정 / MapStruct / `@PageableDefault` / Validation 적정성
4. **보안** — 인가 매처 / 입력 검증 / 권한 우회 / 민감 정보 노출
5. **성능** — N+1 / fetch 전략 / 페이지네이션 누락
6. **컨벤션 준수도** — 따랐지만 이 case 에 적절한가 / 침묵 영역의 일관성 / 더 나은 대안
7. **API / 응답** — HTTP status code 정확성 / 응답 envelope / `@ApiResponses` 커버 (401/403 포함) / 멱등성
8. **테스트** — 커버 범위 (핵심 + edge + 권한) / 어설션 의미성 / 통합 테스트 필요성

**반영 방식**:
- 구현 모델이 **자동 반영 X**. LLM 리뷰는 false positive 있고 의도된 결정을 "문제" 로 잡을 수 있음
- 리뷰 결과 → 사용자 읽음 → 반영 항목 선별 → 현재 세션의 구현 모델이 수정 적용
- 변경 있으면 §4-9 spotlessApply + §4-10 build 재실행 후 §4-12 로 진행

#### 4-12. roadmap 갱신

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

### plan + convention 밖 결정 (있는 경우만)
- {§0 plan 합의 / 이슈 / (있으면) spec / convention 어디에도 없어 구현 중 새로 만든 결정만. convention 따라 자동 적용된 mechanical detail (패키지 경로 / @Transactional 위치 / Swagger 어노테이션 / MapStruct / 로그 포맷 등) 은 적지 않음}
```

보고 직후 머지까지 같은 세션에서 끝낸다 ([`AGENTS.md` 워크플로우 3단계](../../../AGENTS.md) / [`git-workflow.md §4`](../../../docs/git-workflow.md)):

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

## 중간 질문 — 자연스럽게 (강제 검토 X)

다음 상황에서만 사용자에게 묻기 (대부분은 §0 plan 단계에서 이미 합의돼 있어야 정상):
- **plan + 이슈 + (있으면) spec + convention 모두 침묵하는 결정 발견** — mechanical 이면 convention 에서 가져오고, 정책성이면 사용자에게. 예: 외부 API URL / 시크릿 / 환경 변수, 명시되지 않은 도메인 특수 분기
- 빌드 / 테스트가 단순 수정으로 안 되는 실패
- 이슈 / spec / plan 해석이 두 가지 이상 가능한 모호함
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
| 이슈 본문 부재 / `gh issue view` 실패 | 사용자에게 알리고 중단 |
| spec 파일 여러 개 매칭 | 사용자에게 선택 받기 (옵트인이므로 부재는 정상) |
| 마이그레이션 V 번호 중복 | timestamp 형식이라 거의 없음. 발생 시 +1초로 조정 |
| 빌드 실패 (단순) | 자동 수정 후 재실행 (1~2회) |
| 빌드 실패 (복잡) | 사용자에게 원인 보고 + 결정 받기 |
| 외부 API 키 / 시크릿 부재 | 환경 변수 설정 안내 + 중단 |

## 주의

- **worktree 안에서 실행** — 시작 가드 필수. 주 디렉터리(`develop`/`main`)면 중단하고 worktree 로 안내
- **plan mode 필수 (§0)** — 첫 코드 / 파일 편집 전 무조건 plan mode 진입. 영향도 높은 결정은 옵션으로 던지고 사용자 합의 후 진행. plan 합의 = spec 의 "구현 전 결정 검토" in-session 대체
- **이슈 + plan 따름 + convention 위임** — 이슈 본문 + plan 합의 가 1차 source. mechanical detail 은 convention 문서에서 가져온다 (§2 convention 매핑 표 참조). 셋 다 침묵하면 사용자에게 질문
- **spec 파일은 옵트인** — 있으면 함께 따르고, 없어도 차단되지 않음. handoff 가 필요한 케이스에서만 `/spec` 명시 호출
- **마이그레이션 V 번호 = timestamp** — 머지된 파일 수정 X (CLAUDE.md)
- **테스트 동시 작성** — Service 단위 + Controller @WebMvcTest 항상. 핵심 흐름 (가입 / 주문 / 결제 / 환불) 은 통합 테스트도 (단계 4-8). 작성 순서보다 *함께 있는지* 가 중요 (AI 자기참조 검증 위험 보완)
- **한국어 테스트 메서드명** ([test-convention.md](../../../docs/test-convention.md))
- **결과 보고 = 사용자 검토 시점** — `/impl` 끝나면 사용자가 검토 → OK 면 커밋 / PR (3단계 머지, 스킬 없음)
- **PowerShell 5.1**: 한글 메서드명 / 주석은 UTF-8 (Write tool 기본). Gradle 인코딩 이슈는 빌드 실패 시점에 진단
