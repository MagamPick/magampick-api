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
- API URL·응답·에러·날짜: [`docs/api-convention.md`](docs/api-convention.md)
- 인증·인가: [`docs/auth.md`](docs/auth.md)

### 테스트
- [`docs/test-convention.md`](docs/test-convention.md) 의 B 강도 정책 따름
- **코드 작성 시 함께 작성**: Service 단위 + Controller `@WebMvcTest`
- **명시 요청 시 또는 핵심 흐름에만**: Repository `@DataJpaTest` (커스텀 쿼리), 통합 테스트 (가입 / 주문 / 결제 / 환불)
- **작성하지 않음**: 기본 CRUD Repository, MapStruct, Getter/Setter

### DB
- ERD 큰 그림: [`docs/erd/overview.md`](docs/erd/overview.md)
- 새 도메인 / Entity 진입 시 다음 셋을 **함께 작성**:
  1. Entity (`@Entity`) 정의
  2. 마이그레이션 SQL: `src/main/resources/db/migration/V{YYYYMMDDHHMMSS}__*.sql`
  3. ERD 상세: `docs/erd/tables/{table}.md` (컬럼 / 인덱스 / 제약 / 관계)
- **마이그레이션 V 번호 = timestamp (`YYYYMMDDHHMMSS`)** — worktree 병렬 작업 시 충돌 방지 (단, 기존 V1 은 그대로 유지)
- **이미 머지된 마이그레이션 파일은 절대 수정하지 않는다** — 변경은 새 파일로

### Git
- 커밋 메시지: [`docs/commit-convention.md`](docs/commit-convention.md)
- 브랜치·PR·머지: [`docs/git-workflow.md`](docs/git-workflow.md)
- **커밋 전 메시지 검토 필수** — `git commit` 실행 **전에** 작성한 커밋 메시지 전문과 해당 커밋에 포함될 파일 목록을 사용자에게 보여주고 확인받는다. 검토 없이 임의로 메시지를 작성해 커밋 / 푸시 금지. 여러 커밋이면 각각 보여줄 것

---

## 워크플로우

새 기능 개발은 **4단계**로 진행.

| 단계 | 명령/스킬 | 산출물 | 사용자 검토 |
|---|---|---|---|
| 1. 이슈 | `/issue {기능명}` | GitHub Issue (정책 / scope 결정) | 생성 전 |
| 2. 명세 | `/spec {이슈번호}` | `docs/specs/{N}-{기능명}.md` (구현 명세 + 구현 결정) | 저장 전 |
| 3. 구현 | `/impl {이슈번호}` | 코드 + 테스트 + 빌드 통과 | 진행 중 선택 |
| 4. 머지 | 별도 진행 | 작업 브랜치 push + PR 생성 | 커밋 / PR 전 |

> **`main` / `develop` 으로 직접 push 금지.** 항상 작업 브랜치 (`{type}/{이슈번호}-{설명}`) → PR (`base: develop`) → 머지. 예외 없음.
>
> **작업 브랜치는 `/spec` 시작 시 생성** — `gh issue develop {이슈번호}` 로 GitHub 이슈에 연결된 브랜치 (`feat/{이슈번호}-{슬러그}`) 를 만들고, `/spec`·`/impl` 은 이 브랜치에서 작업. `/impl` 은 시작 시 작업 브랜치인지 확인하고 `develop`/`main` 이면 중단.

### 단계별 docs 수정 범위

| 단계 | 수정 OK | 수정 X (별도 이슈) |
|---|---|---|
| `/issue` | `product.md` / `features.md` / `policy.md` / `glossary.md` | 전역 코딩 컨벤션 |
| `/spec` | `docs/erd/overview.md` 의 미정 사항 | 전역 코딩 컨벤션 |
| `/impl` | `docs/erd/tables/{table}.md` (해당 도메인 ERD) / `auth.md` (인증·인가 정책) / `docs/roadmap.md` (해당 기능 행 상태·이슈 번호) | api-convention / coding-convention / test-convention / commit-convention / git-workflow |

> 한 이슈 = 한 PR. 컨벤션 수정 같이 가면 PR 비대 → 별도 이슈로 분리.

### spec 미정 발견 시
`/spec` 작성 중 정책 / scope 미정 발견 → **`/issue` 로 돌아가 결정** → `/spec` 재호출.

### 병렬 운영

- 동시 구현 작업은 1~2개 정도로 제한. 3개 이상은 머지 충돌 / 컨텍스트 부담이 커진다.
- 의존성 있는 도메인은 동시에 구현하지 않는다. 머지 순서 = 도메인 의존성 (`users → stores → products → orders → ...`).
- 별도 터미널 / 세션으로 병렬 작업 시 `git worktree` 로 디렉터리 분리 필수. 같은 디렉터리에서 두 세션이 `.git` 을 공유하면 한쪽의 checkout 이 다른 세션 브랜치를 바꿀 수 있다.
- 로컬 docker compose DB 는 머지 후 적용. 개발 중엔 Testcontainers 만 사용.

### AI Agent 호환성

- 공통 프로젝트 규칙은 `AGENTS.md` 를 단일 원본으로 둔다.
- Claude Code 호환 파일은 `CLAUDE.md` 와 `.claude/skills/` 를 사용한다.
- Codex 워크플로우 원본은 `.codex/skills/magampick-workflow/` 를 사용한다.
- Codex에서 `/issue`, `/spec`, `/impl` 요청을 받으면 `.codex/skills/magampick-workflow/SKILL.md` 를 먼저 읽고, 필요한 `references/*.md` 를 따른다.
- `/issue`, `/spec`, `/impl` 절차를 바꿀 때는 `.claude/skills/` 와 `.codex/skills/magampick-workflow/` 가 서로 어긋나지 않게 함께 갱신한다.
- `~/.codex/skills/magampick-workflow/` 는 Codex 자동 발견용 설치본이다. repo 원본과 다르면 repo 원본을 기준으로 맞춘다.

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
