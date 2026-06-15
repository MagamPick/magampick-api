# Git 워크플로

## 1. 브랜치 모델

Git Flow 변형 — `release` 브랜치는 사용하지 않음.

| 브랜치 | 역할 | 분기 from | 머지 to |
| --- | --- | --- | --- |
| `main` | 운영 코드 (항상 배포 가능 상태) | — | — |
| `develop` | 다음 릴리스 통합 브랜치 | `main` | `main` |
| `feat/*`, `fix/*`, `refactor/*`, `docs/*`, `chore/*` | 일반 작업 | `develop` | `develop` |
| `hotfix/*` | 운영 긴급 수정 | `main` | `main` + `develop` |

---

## 2. 브랜치 네이밍

형식: `<type>/<슬러그>[-step<N>]`

- `type` 은 [커밋 컨벤션](commit-convention.md) 의 타입과 동일 (`feat`, `fix`, `refactor`, `docs`, `chore` 등)
- 슬러그는 노션 페이지 제목 → [`glossary.md`](glossary.md) 영문 매핑 → kebab-case
- 한 노션 페이지를 여러 PR 로 쪼갠 경우 `-step1`, `-step2` 접미 (예: `feat/store-registration-step1`)
- 운영 핫픽스는 `hotfix/<슬러그>` (노션 없이 사용자 직접 지시)

**예시**

- `feat/login-api`
- `feat/store-registration-step1`
- `fix/jwt-expiry`
- `refactor/payment-split`
- `docs/readme-update`
- `chore/workflow-notion-migration`
- `hotfix/prod-crash`

> GitHub Issue 번호는 브랜치명에 포함하지 않는다 — 노션 도입으로 이슈 시스템 사용 중단. 추적은 PR + 노션 페이지 본문 체크리스트.

---

## 3. 기본 작업 흐름

1. **노션 명세 페이지 작성** — 사용자가 노션 "기능 명세 (Features)" DB 에 페이지 작성 (백엔드 레포 변경 없음, 별도 단계)
2. **`/impl <노션URL>` 호출 (모드 A — 메인 디렉터리)** — 노션 fetch → plan mode 합의 → 노션 상태 `기획`→`개발중` → 빈 슬롯에 브랜치 attach
   ```sh
   git -C ../magampick-api-wtX switch -c feat/<슬러그> origin/develop
   ```
3. **`/impl <노션URL>` 호출 (모드 B — 슬롯)** — TDD red→green 구현 + 빌드 + (해당 시) 외부 모델 리뷰 → 커밋 → 푸시
4. **PR 생성** — base: `develop`, 본문에 노션 URL 명시 (쪼갠 경우 `Step N/M` 표기)
5. **CI watch + 자동 머지** — CI green 시 머지
6. **슬롯 정리 + develop pull + 노션 상태 갱신** — `개발중`→`운영중` (또는 본문 체크리스트 체크)

---

## 4. PR 정책

- **머지 전략**: Merge commit — GitHub 의 "Merge pull request" (브랜치 커밋 보존 + 머지 커밋 1개 생성)
- **PR 제목**: [커밋 컨벤션](commit-convention.md) 형식으로 작성 (PR 목록 가독성용)
  - 예: `✨ feat: 로그인 API 엔드포인트 추가`
- **PR 본문**: `.github/pull_request_template.md` 따름. **노션 페이지 URL 명시 필수** (쪼갠 경우 `Step N/M` 표기)
- **CI 통과 필수**: PR 생성 시 `.github/workflows/ci.yml` 의 Build & Test 잡이 자동 실행 (`./gradlew build` = 컴파일 + Spotless + 테스트 + Jacoco)
- **코드 리뷰 생략**: 사람 코드 리뷰 단계를 두지 않는다. CI 통과를 머지 게이트로 삼고, CI 가 green 이면 머지한다. (커밋 메시지 사전 검토 룰은 별개로 유지 — `AGENTS.md` Git 섹션)
- **CI 결과 책임**: PR 생성 후 CI 통과/실패 확인과 머지(또는 실패 수정)까지 같은 세션에서 끝낸다. 머지 전엔 다른 git 작업(브랜치/커밋)을 시작하지 않는다. CI 모니터링 자체는 background 로 돌려도 된다.
- 머지 후 원본 브랜치 삭제 + 노션 상태 갱신

---

## 5. Hotfix 흐름

운영 장애 등 긴급 수정 시:

1. `main` 에서 `hotfix/<슬러그>` 분기 (노션 없이 사용자 직접 지시 OK)
2. 작업 후 `main` 으로 PR + Squash merge
3. **`develop` 에 동기화** — hotfix가 누락되지 않도록 반드시 반영
   ```sh
   git checkout develop
   git pull
   git merge main
   git push
   ```
