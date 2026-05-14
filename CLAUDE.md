# CLAUDE.md

마감픽 백엔드 작업 시 Claude Code 가 따라야 할 규칙.

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
- **Claude 가 코드 작성 시 함께 작성**: Service 단위 + Controller `@WebMvcTest`
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

---

## 워크플로우

새 기능 개발은 **4단계**로 진행.

| 단계 | 도구 | 산출물 | 사용자 검토 |
|---|---|---|---|
| 1. 이슈 | `/issue {기능명}` | GitHub Issue (정책 / scope 결정) | ✅ 생성 전 |
| 2. 명세 | `/spec {이슈번호}` | `docs/specs/{N}-{기능명}.md` (구현 명세 + 구현 결정) | ✅ 저장 전 |
| 3. 구현 | `/impl {이슈번호}` | 코드 + 테스트 + 빌드 통과 | (선택) 진행 중 |
| 4. 머지 | (스킬 없음) | 작업 브랜치 push + PR 생성 — Claude 가 [`commit-convention`](docs/commit-convention.md) / [`git-workflow`](docs/git-workflow.md) 따라 실행 | ✅ 커밋 / PR 전 |

> **`main` / `develop` 으로 직접 push 금지.** 항상 작업 브랜치 (`{type}/{이슈번호}-{설명}`) → PR (`base: develop`) → 머지. 예외 없음.
>
> **작업 브랜치는 `/spec` 시작 시 생성** — `gh issue develop {이슈번호}` 로 GitHub 이슈에 연결된 브랜치 (`feat/{이슈번호}-{슬러그}`) 를 만들고, `/spec`·`/impl` 은 이 브랜치에서 작업. `/impl` 은 시작 시 작업 브랜치인지 확인하고 `develop`/`main` 이면 중단.

### 단계별 docs 수정 범위

| 단계 | 수정 OK | 수정 X (별도 이슈) |
|---|---|---|
| `/issue` | `product.md` / `features.md` / `policy.md` / `glossary.md` | 전역 코딩 컨벤션 |
| `/spec` | `docs/erd/overview.md` 의 미정 사항 | 전역 코딩 컨벤션 |
| `/impl` | `docs/erd/tables/{table}.md` (해당 도메인 ERD) / `auth.md` (인증·인가 정책) | api-convention / coding-convention / test-convention / commit-convention / git-workflow |

> 한 이슈 = 한 PR. 컨벤션 수정 같이 가면 PR 비대 → 별도 이슈로 분리.

### spec 미정 발견 시
`/spec` 작성 중 정책 / scope 미정 발견 → **`/issue` 로 돌아가 결정** → `/spec` 재호출.

### 모델 운영

| 작업 | 모델 | 비고 |
|---|---|---|
| `/issue`, `/spec`, 사용자 대화 / 결정 | **Opus** | `settings.json` 의 기본 모델 |
| `/impl` (구현) | **Sonnet** | Agent 위임 시 `model: "sonnet"` 명시 / 별도 터미널이면 `/model sonnet` 수동 전환 |

Agent 위임 예 (메인 세션 Opus 안에서):

> `Agent(prompt="impl SKILL.md 따라 이슈 12 구현", model="sonnet", isolation="worktree", run_in_background=true)`

### 병렬 운영 (Agent 위임)

도메인 많고 빌드 시간 긴 마감픽 특성상, **메인 세션에서 Agent 위임으로 구현 백그라운드 실행** 권장.

```
[메인 세션]
  사용자: /spec 13  (다음 도메인 명세 대화)
  ↓ 명세 작성 중...
  ↑ (백그라운드 Agent 완료 알림 도착)
  Claude: "impl #5 완료됨. 결과: ..."

[백그라운드 Agent]
  메인 세션이 띄운 Agent (isolation=worktree, run_in_background=true)
  └─ .claude/skills/impl/SKILL.md 따라 자율 실행
     ├─ spec 파싱 → 구현 → 빌드 통과까지
     └─ 완료 시 메인 세션에 자동 알림 (다음 사용자 입력 시점)
```

호출 패턴 (메인 세션 안에서):

> "이슈 #5 (users) 백그라운드 구현 위임"
> → 메인 Claude 가 `Agent(prompt="impl SKILL.md 따라 이슈 #5 구현. 빌드 통과까지. 막힘 / 결정 사항은 결과에 명시.", isolation="worktree", run_in_background=true)` 호출

룰:
- **동시 Agent 1~2개 적당** — 3개+ 머지 충돌 / 메인 컨텍스트 부담 ↑
- **사용자 대화는 메인 세션만** — Agent 는 막힘 시 결과 보고에 명시 (직접 사용자 질문 X)
- **알림 방식** — 백그라운드 Agent 완료 시 다음 사용자 입력 응답에 자동 포함 (즉시 push 알림 X)
- **머지 순서 = 도메인 의존성** (`users → stores → products → orders → ...`)
- **의존성 있는 도메인 동시 X** — Agent 위임 전에 의존 도메인 머지 끝나야
- **마이그레이션 V 번호 = timestamp** (위 DB 섹션) — 동시 작업 충돌 방지
- **로컬 docker compose DB 는 머지 후 적용** — 개발 중엔 Testcontainers 만
- **4단계 머지도 Agent 위임 가능** — CI 대기 (`gh pr checks --watch`) 가 메인 세션을 블로킹하므로. **커밋(메시지 확인)까지 메인 세션**, `push → gh pr create → CI watch → merge → develop pull → 로컬 브랜치 삭제` 는 Agent

> 별도 터미널 띄우는 방식은 진짜 긴 빌드 (10분+) / 메인 컨텍스트 절약이 우선일 때만 검토.

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
