# AGENTS.md

마감픽 백엔드 작업 시 AI coding agent 가 따라야 할 공통 규칙.

---

## 프로젝트 단계

**졸업 프로젝트 / 출시 전.** 일부 정책은 미정이거나 Mock 상태:

- 휴대폰 본인인증: 외부 API 미연동, Mock 구현 ([`auth.md` §8](docs/auth.md))
- Rate Limiting / 이메일 발송 인프라: 출시 시점 도입
- `customers.phone` / `sellers.phone` UNIQUE 미적용 (시연 편의)
- 상세 Pending Decisions 는 [`docs/product.md`](docs/product.md) / [`docs/auth.md`](docs/auth.md) 의 해당 섹션 참조

---

## 작업 규칙

### 의사결정
- 명세에 없는 결정 (정책 / 컨벤션 / 구조) 은 **임의로 가정하지 않고** 사용자에게 묻거나 옵션을 제시
- 합리적 기본값이 있어도 doc / 코드에 반영하기 **전에** 확인 받기
- "권장 / 추천 / 일반적" 같은 표현은 옵션 제시 단계까지만. **적용 단계에선 사용자 확정 필요**

### 도메인 이해
- 작업 전 [`docs/product.md`](docs/product.md) 의 서비스 범위·플랫폼·제약 확인. **Out of Scope / Pending Decisions 임의 가정 금지**
- 신규 기능은 [`docs/features.md`](docs/features.md) scope 안에서만
- 결제·정산·환불·포인트·쿠폰·알림·노쇼 작업은 [`docs/policy.md`](docs/policy.md) 정책 확인

### 언어
- 사용자와의 대화: **한국어**
- 코드 주석: 한국어 OK (의도 / 맥락 설명 시)
- 테스트 메서드명: **한국어 + 언더바** (예: `매장_등록_성공`)
- 클래스·메서드·변수명: **영문** ([`docs/glossary.md`](docs/glossary.md) 매핑 따름)

### 코드 작성
- 패키지·Entity·DTO·예외: [`docs/coding-convention.md`](docs/coding-convention.md)
- API URL·응답·에러·날짜·Swagger: [`docs/api-convention.md`](docs/api-convention.md)
- 인증·인가: [`docs/auth.md`](docs/auth.md)

### 결정 / 구현 책임 분리
- **노션 기능 명세 페이지** ("기능 명세 (Features)" DB) = 정책 / scope / 도메인 결정 / API 의도 의 single source. 사용자가 작성 후 노션에 게시 — 백엔드 레포 작업의 입력. `/impl` 의 plan mode 중 발견한 결정은 노션 본문에 갱신 (휘발 X — 다른 세션 / 모델도 같은 페이지 보고 동일하게 해석)
- **plan mode (in-session)** = 노션 본문 + convention 위에서 사용자와 합의하는 휘발 결정. 영향도 큰 결정은 옵션으로 제시 후 노션에 반영
- **convention 문서** = mechanical detail 의 single source (Swagger 어노테이션 본문 / 패키지 경로 / `@Transactional` 위치 / MapStruct / 로그 포맷 / ErrorCode 분리 / 표준 Processing Flow / Test Cases 표준 케이스 / 마이그레이션 형식 / 인가 매처 등)
- `/impl` 은 노션 페이지 + plan 합의 + convention 을 본다. 셋 다 침묵하는 결정만 사용자에게 질문

> 이전 워크플로우의 `/issue` (GitHub Issue 생성) / `/spec` (`docs/specs/` 핸드오프) 은 노션 도입과 함께 제거됨. 백업은 [`.claude/skills/_archive/old-workflow/`](.claude/skills/_archive/old-workflow/) / [`.codex/skills/_archive/old-workflow/`](.codex/skills/_archive/old-workflow/).

### 테스트
- [`docs/test-convention.md`](docs/test-convention.md) 의 B 강도 정책 따름
- **TDD red→green 으로 작성**: 테스트 코드 먼저 → 실행해서 빨갛게 떨어지는 거 확인 → 구현 코드 → 다시 실행해서 통과 확인. layer 단위 (Service / Controller)
- **layer 별 적용 범위**: Service 단위 + Controller `@WebMvcTest` 는 항상. 통합 테스트는 핵심 흐름 (가입 / 주문 / 결제 / 환불) 만. Repository `@DataJpaTest` 는 커스텀 쿼리만
- **작성하지 않음**: 기본 CRUD Repository, MapStruct, Getter/Setter, DTO / Mapper 자체
- **TDD 불가 레이어**: Entity / 마이그레이션 / DTO / Mapper (스키마 / 시그니처 — 작성 후 컴파일 통과 확인)

### DB
- ERD 큰 그림: [`docs/erd/overview.md`](docs/erd/overview.md)
- 새 도메인 / Entity 진입 시 다음 셋을 **함께 작성**:
  1. Entity (`@Entity`) 정의
  2. 마이그레이션 SQL: `src/main/resources/db/migration/V{YYYYMMDDHHMMSS}__*.sql`
  3. ERD 상세: `docs/erd/tables/{table}.md` (컬럼 / 인덱스 / 제약 / 관계)
- **마이그레이션 V 번호 = timestamp (`YYYYMMDDHHMMSS`)** — worktree 병렬 작업 시 충돌 방지 (단, 기존 V1 은 그대로 유지)
- **이미 머지된 마이그레이션 파일은 절대 수정하지 않는다** — 변경은 새 파일로

### Git
- 커밋 메시지: [`docs/commit-convention.md`](docs/commit-convention.md) — `<emoji> <type>: <subject>` 한 줄만. **body / footer 사용 안 함**
- 브랜치·PR·머지: [`docs/git-workflow.md`](docs/git-workflow.md)
- **커밋 전 메시지 검토 필수** — `git commit` 실행 **전에** 작성한 커밋 메시지 전문과 해당 커밋에 포함될 파일 목록을 사용자에게 보여주고 확인받는다. 검토 없이 임의로 메시지를 작성해 커밋 / 푸시 금지. 여러 커밋이면 각각 보여줄 것
- **commit-msg hook 1회 셋업** — clone 후 메인 디렉터리에서 한 번: `git config core.hooksPath .githooks`. 모든 worktree 가 같은 `.git/config` 를 공유하므로 슬롯에서 다시 할 필요 없음. body 가 들어간 커밋을 자동 reject 해서 [`commit-convention.md §2`](docs/commit-convention.md) 위반 차단

---

## 워크플로우

작업은 **3단계** (명세 → 구현 → 머지). 모든 type 동일:

| 단계 | 명령 / 도구 | 산출물 | 사용자 검토 |
|---|---|---|---|
| 1. 명세 | 노션 "기능 명세 (Features)" DB 에 페이지 작성 (별도, 백엔드 레포 작업 아님) | 노션 페이지 (상태: `기획`) — 정책 / scope / API 의도 / 도메인 결정 | 노션에서 직접 |
| 2. 구현 | `/impl <노션URL>` (모드 A: 메인 → plan + 슬롯 attach, 모드 B: 슬롯 → TDD 구현 + 빌드 + 외부 리뷰) | plan 합의 → 노션 상태 `기획`→`개발중` → 코드 / 테스트 (TDD red→green) → 빌드 통과 + (feat/fix/refactor) 외부 모델 리뷰 → 반영 | plan 합의 전, 리뷰 결과 검토 시 |
| 3. 머지 | `/impl` 끝에서 이어 진행 | 커밋 → 푸시 → PR (본문에 노션 URL 명시) → CI watch → CI green 시 자동 머지 → 슬롯 정리 → develop pull → 노션 상태 갱신 (`개발중`→`운영중` 또는 본문 체크리스트 체크) | 커밋 메시지 전, PR 본문 전 |

> **모든 type 동일 (3단계)**: 명세 → `/impl` → 머지. 적용 단계는 type 에 따라 다름 — [`.claude/skills/impl/SKILL.md`](.claude/skills/impl/SKILL.md) §"Type 분기" 참조.
>
> **GitHub Issue 사용 안 함**: 노션이 이슈를 대체. 추적은 PR + 노션 본문 체크리스트.

> **`main` / `develop` 으로 직접 push 금지.** 항상 작업 브랜치 (`{type}/{슬러그}[-step{N}]`) → PR (`base: develop`) → 머지. 예외 없음.
>
> **3단계 = 머지까지 같은 세션에서 끝.** `/impl` 의 빌드 통과 후 사용자가 커밋 메시지 + PR 본문을 OK 하면, 그 시점에 머지까지 위임된다. CI green = 머지 게이트 ([`git-workflow.md §4`](docs/git-workflow.md)). 세션은 `gh pr checks --watch` 로 CI 결과를 기다리다가 green 즉시 머지 → 슬롯 정리 → develop pull → 노션 상태 갱신 → 사이클 완료 보고. CI red 면 원인 보고 후 다음 액션은 사용자와 결정.
>
> **작업 브랜치는 `/impl` 모드 A 에서 슬롯에 attach** — 노션 페이지 제목 → glossary 영문 매핑 → kebab-case 슬러그 → 빈 슬롯 (`magampick-api-wt1/wt2/wt3` 중 detached HEAD 인 곳) 에 `git -C ../magampick-api-wtX switch -c {type}/{슬러그} origin/develop` 으로 attach. 이후 모드 B (`/impl <노션URL>` 슬롯에서 재호출 또는 `EnterWorktree` 진입) 는 **그 슬롯 디렉터리에서** 실행한다. 시작 시 슬롯 위치인지 확인하고 메인 디렉터리 (`develop`/`main`) 이면 모드 A 부트스트랩으로 분기. 자세한 슬롯 운영 룰은 §"병렬 운영" 참조.

### 단계별 docs 수정 범위

**`feat` / `fix` 워크플로우 기준** — `refactor` / `docs` / `chore` 는 작업 scope 안에서 자유롭게 수정:

| 단계 | 수정 OK | 수정 X (별도 작업) |
|---|---|---|
| 명세 (노션) | 노션 페이지 본문 (정책 / scope / 결정) — 백엔드 레포 변경 없음 | 백엔드 레포의 docs / 코드 |
| `/impl` | `docs/erd/overview.md` 의 미정 사항 / `docs/erd/tables/{table}.md` (해당 도메인 ERD) / `auth.md` (인증·인가 정책 — 노션에도 반영) / `docs/roadmap.md` (해당 기능 행 상태 / 노션 URL / PR 번호) | api-convention / coding-convention / test-convention / commit-convention / git-workflow |

> 한 노션 페이지 = 1+ PR (쪼갠 경우 본문 체크리스트로 단계 추적). 컨벤션 수정 같이 가면 PR 비대 → 별도 chore 로 분리.

### plan / 구현 중 미정 발견 시
`/impl` 의 plan mode 또는 구현 중 정책 / scope 미정 발견 → **옵션 제시 → 사용자 결정 → 노션 페이지 본문 갱신** (휘발 X) → 진행. 임의 가정 금지.

### 병렬 운영

#### Worktree 슬롯 풀

같은 디렉터리에서 두 세션이 `.git` 을 공유하면 한쪽의 checkout 이 다른 세션 브랜치를 바꾼다. 그리고 Windows + Claude / IDE 환경에선 작업마다 worktree 를 생성·제거하면 OS 디렉터리 lock 으로 정리가 막힌다. → 미리 만들어둔 슬롯 풀을 작업마다 갈아끼우는 방식을 쓴다.

**구조 — 메인 + 작업 슬롯 3개 (fungible)**:

```
magampick-api          # 메인 디렉터리. develop 고정 — pull / `/impl` 모드 A / 슬롯 정리의 홈베이스
magampick-api-wt1      # 작업 슬롯 1
magampick-api-wt2      # 작업 슬롯 2
magampick-api-wt3      # 작업 슬롯 3
```

슬롯은 type / 종류 구분 없이 **fungible** — `docs/`, `feat/`, `fix/`, `refactor/`, `chore/` 어느 브랜치든 빈 슬롯에 임의로 attach.

**최초 1회 셋업** (각 머신 / 환경마다):
```sh
git worktree add ../magampick-api-wt1 --detach origin/develop
git worktree add ../magampick-api-wt2 --detach origin/develop
git worktree add ../magampick-api-wt3 --detach origin/develop
```

`--detach` 로 만들어 어느 브랜치도 점유하지 않는 **빈 슬롯** 상태로 둔다.

**규칙**:

- **메인 디렉터리 `magampick-api` 는 항상 `develop` 고정** — pull / `/impl` 모드 A (노션 fetch + plan + 슬롯 attach) / 슬롯 정리 / PR 웹 리뷰의 홈베이스. 여기서 작업 브랜치로 checkout 하지 않는다.
- **모든 작업 브랜치는 슬롯에서** — impl 뿐 아니라 문서 / 컨벤션 수정도 예외 없이 (docs / chore 도 슬롯 사용).
- **슬롯 attach = `/impl` 모드 A 끝에서 1회** — 노션 페이지 제목 → glossary 영문 매핑 → kebab-case 슬러그 → 빈 슬롯에 `git -C ../magampick-api-wtX switch -c {type}/{슬러그}[-step{N}] origin/develop`. 빈 슬롯은 `git worktree list` 에서 `(detached HEAD)` 표시. (노션 페이지 없는 chore / docs 는 사용자가 직접 슬롯 attach 또는 사용자 지시에 따라.)
- **`/impl` 모드 B 는 그 슬롯 디렉터리에서 에이전트를 띄워 실행한다.** 도구 앵커(파일 탐색·셸 cwd·스킬)가 그 디렉터리 기준이어야 하므로, 메인에서 `cd` 로 옮기는 것에 의존하지 않는다. Claude Code 한정으로 `EnterWorktree` 로 같은 세션에서 슬롯 진입 가능 (Codex 엔 없음 — relaunch 필요).
- **빈 슬롯 표시 = detached HEAD on `origin/develop`**. 한 슬롯은 attach 된 동안 한 브랜치만 점유 (git 제약). `develop` 은 메인이 점유 중이라 슬롯에서 `switch develop` 은 실패 — 항상 `--detach origin/develop` 사용.
- **PR 머지 후 정리** — 슬롯 안의 브랜치 떼기 + 로컬·원격 브랜치 삭제. **`git worktree remove` 호출 X** (OS lock 회피):
  ```sh
  git -C ../magampick-api-wtX switch --detach origin/develop  # 슬롯을 빈 상태로
  git branch -D {type}/{슬러그}[-step{N}]                     # 로컬 브랜치 삭제
  # 원격 브랜치는 PR auto-delete 됐으면 생략, 아니면 git push origin --delete {branch}
  ```
- **PR 리뷰** — 가벼운 리뷰는 메인 디렉터리에서 GitHub 웹 / IDE diff 로. 무거운 리뷰가 필요하면 빈 슬롯에 `gh pr checkout {N}` 으로 잠깐 attach 후 다시 detach.
- **임시 슬롯 추가** — 슬롯 3개 다 점유 중인데 핫픽스가 필요하면 `magampick-api-wt4` 같이 임시 추가 OK. 작업 후 detach 로 비우거나 `git worktree remove` 로 완전 제거. 일반 운영엔 3개로 충분.
- `build/` · Testcontainers 는 슬롯별로 독립 — 격리에 유리. 슬롯 재사용 시 gradle 캐시 / IDE 인덱싱이 그대로 재활용된다.

#### 그 외

- 동시 구현 작업은 1~2개 정도로 제한 (슬롯 3개 중 1개는 docs / 리뷰 / 핫픽스 여유). 3개 이상 동시 impl 은 머지 충돌 / 컨텍스트 부담이 커진다.
- 의존성 있는 도메인은 동시에 구현하지 않는다. 머지 순서 = 도메인 의존성 (`users → stores → products → orders → ...`).
- 로컬 docker compose DB 는 머지 후 적용. 개발 중엔 Testcontainers 만 사용.

### AI Agent 호환성

- 공통 프로젝트 규칙은 `AGENTS.md` 를 단일 원본으로 둔다.
- Claude Code 호환 파일은 `CLAUDE.md` 와 `.claude/skills/` 를 사용한다.
- Codex 워크플로우 원본은 `.codex/skills/magampick-workflow/` 를 사용한다.
- Codex에서 `/impl` 요청을 받으면 `.codex/skills/magampick-workflow/SKILL.md` 를 먼저 읽고, 필요한 `references/*.md` 를 따른다.
- `/impl` 절차를 바꿀 때는 `.claude/skills/` 와 `.codex/skills/magampick-workflow/` 가 서로 어긋나지 않게 함께 갱신한다.
- `~/.codex/skills/magampick-workflow/` 는 Codex 자동 발견용 설치본이다. repo 원본과 다르면 repo 원본을 기준으로 맞춘다.
- 이전 워크플로우 (`/issue` / `/spec`) 의 백업은 `.claude/skills/_archive/old-workflow/` / `.codex/skills/_archive/old-workflow/` 에 보존.

---

## 자주 쓰는 명령

| 용도 | 명령 |
|---|---|
| 빌드 + 테스트 + Spotless 검증 | `./gradlew build` |
| 테스트만 | `./gradlew test` |
| **포맷 적용 — 코드 작성/수정 후 반드시 실행** | `./gradlew spotlessApply` |
| 로컬 환경 실행 | `docker compose up -d` |
| 로컬 환경 정지 | `docker compose down` |
| 로컬 환경 정지 + 볼륨 삭제 | `docker compose down -v` |

---

## 참고 문서

| 문서 | 내용 |
|---|---|
| [`docs/product.md`](docs/product.md) | 서비스 정의 / 범위 / Platform / 의사결정 원칙 |
| [`docs/features.md`](docs/features.md) | 기능 scope |
| [`docs/roadmap.md`](docs/roadmap.md) | 구현 순서 (의존 계층) + 진행 상태 추적 |
| [`docs/policy.md`](docs/policy.md) | 운영 정책 |
| [`docs/glossary.md`](docs/glossary.md) | 도메인 용어 · 영문 매핑 |
| [`docs/coding-convention.md`](docs/coding-convention.md) | 패키지 / Entity / DTO / 예외 / 의존성 |
| [`docs/api-convention.md`](docs/api-convention.md) | URL / 응답 / 페이지네이션 / Swagger / 시간대 |
| [`docs/auth.md`](docs/auth.md) | JWT / 회원가입 / 인가 / 보안 |
| [`docs/test-convention.md`](docs/test-convention.md) | 테스트 종류 · 정책 · Fixture · Testcontainers |
| [`docs/erd/overview.md`](docs/erd/overview.md) | DB ERD 전체 그림 |
| [`docs/commit-convention.md`](docs/commit-convention.md) | 커밋 메시지 규칙 |
| [`docs/git-workflow.md`](docs/git-workflow.md) | 브랜치 / PR / 머지 |

> 새 문서가 추가되면 이 표에 한 줄로 등록.
