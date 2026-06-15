---
name: impl
description: 노션 기능 명세 페이지 → plan mode 합의 → TDD red→green 구현 → 빌드 + 외부 리뷰 → PR 머지 → 노션 상태 갱신. /impl <노션URL> 로 호출. 노션 페이지 (정책 / scope / 도메인 결정) + plan 합의 + convention (mechanical) 이 source. 셋 다 침묵할 때만 사용자에게 질문. 쪼갠 경우 노션 본문 체크리스트로 단계 추적. 메인 디렉터리에서 호출 시 plan 까지 진행 후 슬롯 attach + 안내 (부트스트랩 모드), 슬롯에서 호출 시 코드 작업부터 진행 (실행 모드).
---

# /impl — 구현

마감픽 워크플로우. **노션 기능 명세 페이지** 를 single source 로 받아 → plan mode → TDD → 구현 → PR 머지 → 노션 상태 갱신.

> 노션 페이지 = 정책 / scope / 도메인 결정 / API 의도. plan 합의 = in-session 휘발 결정. convention = mechanical 표준 (Swagger / 패키지 / `@Transactional` / 로깅 / 마이그레이션 형식 등). 셋 다 침묵할 때만 사용자에게 질문.
>
> **이슈 시스템 사용 안 함**: GitHub Issue 는 만들지 않는다. 명세 = 노션. 추적 = PR + 노션 페이지 본문 체크리스트.

## 입력

- `<노션URL>` — 노션 "기능 명세 (Features)" DB 의 페이지 URL (필수, 예: `/impl https://www.notion.so/...`)

## 두 가지 호출 모드

`/impl` 은 호출 위치에 따라 모드가 다르다:

| 모드 | 호출 위치 | 처리 단계 |
|---|---|---|
| **A. 부트스트랩** | 메인 디렉터리 (`develop` / `main`) | §1 노션 fetch → §2 plan 합의 → §3 노션 "기획"→"개발중" → §4 슬롯 attach → 사용자 안내 + 중단 |
| **B. 실행** | 슬롯 (`feat/<slug>` 등) | §1 노션 fetch (재확인) → §5 TDD 구현 → §6 빌드 + 외부 리뷰 → §7 머지 → §8 노션 상태 갱신 |

판별: `git branch --show-current` / `git worktree list`.

> **Claude Code 한정 편의**: 모드 A 의 §4 끝에서 `EnterWorktree` 로 슬롯 진입 후 모드 B 를 같은 세션에서 이어서 진행 (relaunch 없이 1회 호출로 끝). Codex 엔 없으니 canonical 절차는 두 단계 호출.

---

## §1. 노션 페이지 fetch

```text
mcp__claude_ai_Notion__notion-fetch(id=<노션URL>)
```

읽어야 할 속성:
- `기능명` (title) / `분류` / `사용자` / `상태` / `설명`
- 본문 (Markdown content)
- 릴레이션: `관련 정책`, `관련 결정` (있으면 JSON array of URLs)

**릴레이션 펼침 규칙**:
- 본문 + `설명` 만으로 정책 / scope / 도메인 결정이 충분히 명확 → 릴레이션 fetch skip
- 부족하면 `관련 정책` / `관련 결정` 각 URL 을 별도 `notion-fetch` 호출로 펼침
- `관련 외부연동` / `관련 Phase` 는 본문에서 명시적으로 언급될 때만 펼침

**Type 결정** — 노션 페이지의 의도에 따라:
- 신규 기능 / 동작 변경 → `feat`
- 버그 수정 → `fix`
- 코드 구조 / 가독성 / 성능 (동작 동일) → `refactor`

> `docs` / `chore` 는 노션 명세 DB 와 무관 — 노션 URL 없이 사용자가 직접 지시. 이 SKILL 의 노션 흐름은 거의 `feat` / `fix`.

---

## §2. plan mode 진입 + 합의 (모드 A 에서만)

> 첫 코드 / 파일 편집 **전** 에 plan mode 진입. Claude Code: native plan mode (shift+tab). Codex: 구조화된 plan 을 chat 으로 출력 후 명시적 사용자 승인 대기.

**plan 에 포함**:

1. **노션 페이지 요약** — 기능명 / 분류 / 사용자 / 현재 상태 / 본문 핵심
2. **충분함 체크** — 정책 / scope / API 의도 / 도메인 결정이 노션 본문 + 릴레이션에 명시되어 있나?
   - 빈 곳 / 모호한 부분 발견 → **옵션으로 사용자에게 질문** → 사용자 결정 → **노션 페이지 본문 갱신** (`notion-update-page` 의 `update_content` 또는 `insert_content`) → 휘발 X
   - 결정이 노션에 박혀야 향후 다른 세션 / 모델이 같은 페이지를 봐도 동일하게 해석
3. **영향도 높은 결정** (본문에 명시 안 된 부분만 옵션으로):
   - 다중성 / 카디널리티 (1:1 vs 1:N, 단일 vs 다중 선택)
   - 권한 분기 (role 별 동작)
   - 인덱스 / 유니크 영향
   - 마이그레이션 영향 (기존 NOT NULL 추가 등)
   - Enum 후보 / 상태값 누락
   - 외부 시스템 의존 (Mock vs 실연동)
4. **쪼개기 합의** — 한 PR 로 갈지, 여러 PR 로 단계 분할할지 사용자와 결정
   - 한 PR: 그대로 진행
   - 여러 PR: **노션 페이지 본문에 단계 체크리스트 작성** (예: `- [ ] Step 1: Entity + 마이그레이션`, `- [ ] Step 2: Service + Controller`, ...). 이번 `/impl` 호출은 **첫 단계만** 진행
5. **적용 단계 합의** — Type 분기 표 기준으로 어디부터 어디까지

**합의 룰**:
- 노션 / convention 셋 다 침묵하는 결정만 사용자에게 질문
- 노션 본문에 이미 박힌 결정은 plan 에 중복 검토 X
- plan 합의 = "이렇게 진행" 동의. plan exit 후 §3 → §4

### Type 분기 (적용 단계 결정)

| Step | feat / fix | refactor |
|---|---|---|
| §5 TDD 구현 (Entity ~ Controller + 테스트) | 전체 | 해당 layer 만 |
| §5-6 통합 테스트 (핵심 흐름) | 가입 / 주문 / 결제 / 환불 시 | 해당 시 |
| §5-7 spotlessApply | ✓ | ✓ |
| §5-8 build | ✓ | ✓ |
| §6 외부 모델 리뷰 | ✓ | ✓ |
| §7 roadmap 갱신 | ✓ | 해당 시 |
| §8 머지 + 노션 상태 갱신 | ✓ | ✓ |

---

## §3. 노션 상태 "기획" → "개발중" (모드 A)

```text
mcp__claude_ai_Notion__notion-update-page(
  page_id=<페이지ID>,
  command="update_properties",
  properties={ "상태": "개발중" }
)
```

쪼갠 경우 첫 단계 시작 시점에 한 번만 변경. 이후 PR 들은 본문 체크리스트만 업데이트.

---

## §4. 슬롯 attach (모드 A)

**슬러그 추출**:
1. 노션 페이지 `기능명` (title) 에서 한국어 기능명 추출
2. [`glossary.md`](../../../docs/glossary.md) 영문 매핑으로 변환 → kebab-case (예: `매장 등록 신청` → `store-registration`)
3. glossary 미정 용어는 사용자에게 옵션 제시 + 확정

쪼갠 경우: 슬러그 뒤에 `-step{N}` 접미 (예: `store-registration-step1`).

**빈 슬롯 찾기**:
```powershell
git worktree list
```
`(detached HEAD)` 표시된 슬롯이 빈 슬롯. 기본 풀: `magampick-api-wt1/wt2/wt3` ([AGENTS.md §"병렬 운영"](../../../AGENTS.md)). 모두 점유 시 사용자에게 정리 / 임시 슬롯 추가 여부 확인 후 중단.

**브랜치 생성 + 슬롯 attach** (`gh issue develop` 안 씀 — 이슈 없음):
```powershell
git -C ../magampick-api-wtX switch -c feat/<slug> origin/develop
```
- type 이 feat 가 아니면 prefix 조정 (`fix/`, `refactor/`)
- origin push 는 첫 커밋 후 `git push -u origin feat/<slug>` 로 (§8 의 커밋 사이클에서 처리)

**결과 보고 + 중단**:
> ✅ 슬롯 attach: `../magampick-api-wtX` (브랜치: `feat/<slug>`)
> 노션 상태: 기획 → 개발중
> 다음 단계: 그 디렉터리에서 에이전트 띄워 `/impl <노션URL>` 재실행 (모드 B 진입)
>
> Claude Code 한정: `EnterWorktree` 로 슬롯 진입 후 이어서 진행 가능 (relaunch 대체)

---

## §5. TDD red → green 구현 (모드 B)

> **핵심 원칙**: 테스트 코드 작성 → 실행해서 빨갛게 떨어지는 거 확인 (red) → 구현 코드 → 다시 실행해서 통과 확인 (green). Service / Controller layer 마다 사이클 반복.

### 5-1. Entity / 마이그레이션 / ERD (TDD 불가 — 스키마 변경)

작성 후 컴파일 통과 확인.

- **Entity**: `src/main/java/{package}/{domain}/entity/{Name}.java`
  - `@Entity` + JPA 어노테이션 ([coding-convention.md](../../../docs/coding-convention.md))
  - Enum = `@Enumerated(EnumType.STRING)`, 위치 = `Point` (`org.locationtech.jts.geom.Point` + Hibernate Spatial)
  - BaseEntity 상속 (createdAt / updatedAt / deletedAt) — 있으면
- **마이그레이션**: `src/main/resources/db/migration/V{YYYYMMDDHHMMSS}__{설명}.sql`
  - timestamp 형식. 머지된 파일 수정 X
  - Enum = `VARCHAR + CHECK`, 위치 = `GEOGRAPHY(POINT, 4326)` + GIST 인덱스, KST timezone
- **ERD**: `docs/erd/tables/{table}.md`

### 5-2. Repository

- `extends JpaRepository<Entity, Long>`
- 기본 CRUD: 테스트 X
- 커스텀 쿼리: `@DataJpaTest` 먼저 작성 → 실행 → red → 쿼리 구현 → green

### 5-3. Service (TDD red→green)

**1. 테스트 코드 먼저**: `src/test/java/{package}/{domain}/service/{Name}ServiceTest.java`
- 노션 본문의 Business Logic + plan 합의 + Validation Rules 에서 표준 케이스 도출
- **한국어 메서드명** (예: `매장_등록_성공`) + `// given-when-then` 주석
- Mockito + AssertJ

**2. 실행해서 red 확인**:
```powershell
./gradlew test --tests "*{Name}ServiceTest"
```
컴파일 에러 또는 실패 (red) 확인. green 이면 테스트가 잘못 작성된 것 (Service 가 아직 없는데 통과한다? 점검).

**3. Service 구현**: `src/main/java/{package}/{domain}/service/{Name}Service.java`
- 비즈니스 로직 = 노션 본문 + plan 합의
- 트랜잭션 경계 = 기본 Service 메서드 단위 (coding-convention §3)
- 예외 = `BusinessException` + `BaseErrorCode` ([coding-convention.md](../../../docs/coding-convention.md))

**4. 재실행 → green 확인**:
```powershell
./gradlew test --tests "*{Name}ServiceTest"
```

### 5-4. DTO + MapStruct Mapper (TDD 불가 — 시그니처)

- Request / Response 분리 ([api-convention.md](../../../docs/api-convention.md))
- 검증 = Bean Validation 어노테이션
- Mapper = MapStruct (coding-convention §9)
- DTO / Mapper 자체 테스트 X

### 5-5. Controller (TDD red→green)

**1. 테스트 먼저**: `src/test/java/{package}/{domain}/controller/{Name}ControllerTest.java`
- 노션 API 의도 + plan 합의에서 Controller 테스트 케이스 도출
- MockMvc + Mockito (Service mock)
- 인증 시 `@WithMockUser` 또는 SecurityContext 설정 ([auth.md](../../../docs/auth.md))

**2. 실행 → red**:
```powershell
./gradlew test --tests "*{Name}ControllerTest"
```

**3. Controller 구현**: `src/main/java/{package}/{domain}/controller/{Name}Controller.java`
- URL / 메서드 / 상태 코드 = 노션 API 의도 + plan 합의 + [api-convention.md](../../../docs/api-convention.md)
- `@RestController` + `@RequestMapping`
- 응답 = `ApiResponse<T>` 자동 wrap (ResponseBodyAdvice — payload 만 반환)
- Springdoc OpenAPI 어노테이션 (api-convention §12)
- 인가 = `@PreAuthorize` 또는 SecurityConfig ([auth.md](../../../docs/auth.md))

**4. 재실행 → green**.

### 5-6. 통합 테스트 (핵심 흐름인 경우만)

노션 본문의 Business Logic 이 **핵심 비즈니스 흐름** (가입 / 주문 / 결제 / 환불) 이면 작성. 그 외 skip.

- 위치 = `src/test/java/{package}/{domain}/{Name}IntegrationTest.java`
- `@SpringBootTest @AutoConfigureMockMvc @Transactional` + `extends PostgresTestBase` ([test-convention.md §8 / §10](../../../docs/test-convention.md))
- 실제 DB (Testcontainers PostGIS) + 실제 Service ↔ Repository
- **목적**: mock 가정 어긋나는 부분 (트랜잭션 경계 / FK / 보안 필터 / Validation 흐름) 드러냄. AI 자기참조 검증 위험 차단

TDD 순서 — 통합 테스트 먼저 → red → 위 layer 들 구현 마무리 → green.

### 5-7. spotlessApply
```powershell
./gradlew spotlessApply
```

### 5-8. build (전체)
```powershell
./gradlew build
```

빌드 / 전체 테스트 통과 확인. 실패 시:
- **단순 실패** (오타 / import / 포맷) → 자동 수정 후 재실행 (1~2회)
- **복잡한 실패** (로직 / 노션 본문 해석) → 사용자에게 보고 + 결정 받기

---

## §6. 외부 모델 리뷰 (feat / fix / refactor)

빌드 통과 후, 커밋 전. 구현 모델 blindspot 우회 + 컨벤션 / OO / Spring Boot 관행 비판적 검토 목적.

**모델 선택**:
- 디폴트: **Codex 5.5 medium**
- 작업 시작 시 사용자가 토큰 잔량 / 작업 중요도 보고 다른 모델 선택 가능 (Codex 5.5 high / Opus 4.7 / Sonnet 다른 인스턴스). 구현 모델과 다른 family 권장

**호출 위치**: 현재 작업 슬롯 (read-only — 별도 worktree 안 만들어도 됨)

**호출 방식**:
- Claude Code: `Agent` tool 또는 headless `claude -p ...`
- Codex: 새 세션

**리뷰 prompt** — 다음 8개 카테고리 모두 짚어달라:

1. **의도 정합성** — 노션 페이지 본문 + plan 합의 부합 / scope 안 / over-engineering 없음
2. **캡슐화 / OO** — Tell-Don't-Ask / predicate 명명 (state vs capability) / SRP / 생성 패턴 (builder vs factory) 일관
3. **Spring Boot 관행** — `@Transactional` 경계 / 예외 핸들링 / multipart / MapStruct / `@PageableDefault` / Validation 적정성
4. **보안** — 인가 매처 / 입력 검증 / 권한 우회 / 민감 정보 노출
5. **성능** — N+1 / fetch 전략 / 페이지네이션 누락
6. **컨벤션 준수도** — 따랐지만 case 적합한가 / 침묵 영역 일관성 / 더 나은 대안
7. **API / 응답** — HTTP status code 정확성 / 응답 envelope / `@ApiResponses` 커버 (401/403 포함) / 멱등성
8. **테스트** — 커버 범위 (핵심 + edge + 권한) / 어설션 의미성 / 통합 테스트 필요성

**반영 방식**:
- 자동 반영 X. LLM 리뷰는 false positive 있고 의도된 결정을 "문제" 로 잡을 수 있음
- 리뷰 결과 → 사용자 검토 → 반영 항목 선별 → 현재 구현 모델이 수정 적용
- 변경 있으면 §5-7 spotlessApply + §5-8 build 재실행

---

## §7. roadmap 갱신

[`docs/roadmap.md`](../../../docs/roadmap.md) 의 해당 기능 행:
- 상태 `미착수` → `완료`
- 비고 / 링크 컬럼에 **노션 페이지 URL** + (머지 후) PR 번호 기록

해당 기능 행이 없으면 (scope 신규 추가) 적절한 계층에 행 추가.
빌드 실패 / 중단 시 갱신 X — `완료` 는 구현 + 빌드 통과 후에만.

---

## §8. 결과 보고 + 머지 사이클 + 노션 상태 갱신 (사용자 검토 ★)

빌드 통과 후 사용자에게 보고:

```markdown
## /impl 완료 — 노션 페이지: <기능명>

### 생성 / 수정 파일
- Entity: src/main/java/.../{Name}.java
- 마이그레이션: src/main/resources/db/migration/V{YYYYMMDDHHMMSS}__*.sql
- ERD: docs/erd/tables/{table}.md
- Repository: ...
- Service + 테스트: ...
- DTO / Mapper: ...
- Controller + 테스트: ...
- roadmap: docs/roadmap.md (해당 행)

### 빌드 결과
✅ ./gradlew build 통과

### plan + convention 밖 결정 (있는 경우만)
- {convention 따라 자동 적용된 mechanical detail 은 적지 않음}
```

보고 직후 머지까지 같은 세션에서 끝낸다 ([`AGENTS.md` 워크플로우](../../../AGENTS.md) / [`git-workflow.md §4`](../../../docs/git-workflow.md)):

1. **커밋 메시지 검토** — `<emoji> <type>: <subject>` **한 줄만** ([`commit-convention.md` §2](../../../docs/commit-convention.md) — body / footer 사용 안 함). 작성한 메시지 + 커밋 파일 목록 사용자에게 보여주고 OK 받기. `commit-msg` hook 우회 / `--no-verify` 금지
2. **커밋 + 푸시** — 첫 push 는 `git push -u origin feat/<slug>`
3. **PR 본문 검토** — `gh pr create` 호출 전 제목 / 본문 사용자에게 보여주고 OK 받기. **이 시점이 머지까지 위임받는 동의 시점**. PR 본문에 **노션 페이지 URL** 명시 (쪼갠 경우 `Step N/M` 표기)
4. **PR 생성** — `gh pr create --base develop ...`
5. **CI watch** — `gh pr checks {PR번호} --repo MagamPick/magampick-api --watch` background. 다른 폴링 / sleep 금지
6. **자동 머지** — CI green 시 즉시 `gh pr merge {PR번호} --merge --delete-branch`. 사용자 추가 확인 없이 진행 (CI = 머지 게이트). `gh pr view ... --json state,mergedAt,mergeCommit` 으로 검증
7. **슬롯 정리 + develop pull**:
   ```sh
   git fetch --prune
   git switch --detach origin/develop          # 슬롯을 빈 상태로
   git branch -D feat/<slug>                    # 로컬 브랜치 삭제
   git -C "{메인 디렉터리 절대경로}" pull        # 메인 develop 최신화
   ```
8. **노션 상태 갱신**:
   - **한 PR 로 페이지 전체 완료** → `상태` "개발중" → "운영중"
     ```text
     mcp__claude_ai_Notion__notion-update-page(
       page_id=<페이지ID>,
       command="update_properties",
       properties={ "상태": "운영중" }
     )
     ```
   - **여러 PR 중 일부만 완료** → 본문 체크리스트의 해당 항목만 `[x]` 로 체크 (`update_content` 의 search-and-replace). `상태` 는 그대로 "개발중" 유지
9. **사이클 완료 보고** — PR URL / merge commit / 노션 상태 / 다음 단계 안내

CI red 인 경우: 실패 원인 + 다음 액션 후보 (수정 후 추가 커밋 vs 롤백 vs 상의) 보고 후 사용자 결정 대기. 임의 강제 머지 / 머지 시도 X.

---

## 중간 질문 — 자연스럽게 (강제 검토 X)

다음 상황에서만 사용자에게 묻기 (대부분은 §2 plan 단계에서 이미 합의돼 있어야 정상):
- **노션 본문 + plan + convention 모두 침묵하는 결정 발견** — mechanical 이면 convention 에서 가져오고, 정책성이면 사용자에게 + **결정은 노션에 반영** (휘발 X)
- 빌드 / 테스트가 단순 수정으로 안 되는 실패
- 노션 본문 해석이 두 가지 이상 가능한 모호함
- `auth.md` / `docs/erd/tables/` 갱신 시 정책 결정 필요

## 단계별 docs 수정 권한

**수정 OK** (구현 중):
- `docs/erd/tables/{table}.md` (해당 도메인 ERD 상세)
- `docs/auth.md` (인증 / 인가 정책 결정 필요 시 — 노션 본문에도 반영)
- `docs/roadmap.md` (해당 기능 행 상태 / 노션 URL / PR 번호)

**수정 X (별도 chore PR)**:
- api-convention / coding-convention / test-convention / commit-convention / git-workflow

## convention single source 매핑

노션 / plan 침묵 시 convention 에서 가져온다 — 추측 X, 일관 적용:

| Topic | Source |
|---|---|
| Swagger / OpenAPI 어노테이션 부착 | [`api-convention.md`](../../../docs/api-convention.md) §12 |
| 패키지 / 레이어 / `@Transactional` 위치 / 예외 / 로깅 / MapStruct | [`coding-convention.md`](../../../docs/coding-convention.md) §1~4, §8, §9, §11 |
| 테스트 종류 / 강도 / Fixture / 한국어 메서드명 | [`test-convention.md`](../../../docs/test-convention.md) |
| 인증 / 인가 / 본인 리소스 접근 | [`auth.md`](../../../docs/auth.md) |
| 마이그레이션 / Enum CHECK / Point / KST | [`erd/overview.md`](../../../docs/erd/overview.md) |
| 표준 Processing Flow (JWT 추출 → repository.findById → 404 → dirty checking → Mapper) | 별도 명시 없이 표준대로 |

## 에러 처리

| 상황 | 처리 |
|---|---|
| 노션 URL fetch 실패 | 사용자에게 알리고 중단 (URL 확인 / Notion MCP 권한 점검) |
| 노션 페이지에 정책 / scope 빈 곳 | 사용자에게 옵션 제시 → 결정 → **노션 본문 갱신** 후 진행 |
| 슬롯 모두 점유 | 사용자에게 정리 / 임시 슬롯 추가 여부 확인 후 중단 |
| 마이그레이션 V 번호 중복 | timestamp 형식이라 거의 없음. +1초로 조정 |
| 빌드 실패 (단순) | 자동 수정 후 재실행 (1~2회) |
| 빌드 실패 (복잡) | 사용자에게 원인 보고 + 결정 받기 |
| 노션 상태 갱신 실패 | 사용자에게 알리고 수동 갱신 안내 (머지는 이미 완료 — 별도 처리) |

## 주의

- **노션 single source** — 정책 / scope / 도메인 결정은 노션 본문이 원본. plan 중 새 결정 발견 시 **노션 본문 갱신** (휘발 X)
- **두 모드 명확히** — 모드 A (메인) = plan 까지 + 슬롯 attach, 모드 B (슬롯) = 코드 작업. 한 호출로 끝내려면 Claude Code 의 `EnterWorktree` 사용
- **TDD red→green** — 테스트 작성 → 실행해서 red 확인 → 구현 → 다시 실행해서 green 확인. layer 단위 (Service / Controller). Entity / DTO / Mapper 는 TDD 불가 (스키마 / 시그니처)
- **이슈 시스템 사용 안 함** — GitHub Issue 생성 X. 추적 = PR + 노션 본문 체크리스트
- **마이그레이션 V 번호 = timestamp** — 머지된 파일 수정 X
- **한국어 테스트 메서드명** ([test-convention.md](../../../docs/test-convention.md))
- **명시 승인 게이트 우회 X** — 커밋 메시지 / PR 본문 사용자 OK 필수. `--no-verify` 금지
- **PowerShell 5.1**: 한글 메서드명 / 주석은 UTF-8 (Write tool 기본). Gradle 인코딩 이슈는 빌드 실패 시점에 진단
