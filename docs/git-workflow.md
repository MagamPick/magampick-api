# Git 워크플로

## 1. 브랜치 모델

Git Flow 변형 — `release` 브랜치는 사용하지 않음.

| 브랜치 | 역할 | 분기 from | 머지 to |
| --- | --- | --- | --- |
| `main` | 운영 코드 (항상 배포 가능 상태) | — | — |
| `develop` | 다음 릴리스 통합 브랜치 | `main` | `main` |
| `feat/*`, `fix/*`, `refactor/*`, `docs/*` | 일반 작업 | `develop` | `develop` |
| `hotfix/*` | 운영 긴급 수정 | `main` | `main` + `develop` |

---

## 2. 브랜치 네이밍

형식: `<type>/<이슈번호>-<짧은-설명>`

- `type` 은 [커밋 컨벤션](commit-convention.md) 의 타입과 동일 (`feat`, `fix`, `refactor`, `docs` 등)
- 설명은 영어 kebab-case
- 운영 핫픽스는 `hotfix/<이슈번호>-<설명>`

**예시**

- `feat/123-login-api`
- `fix/124-jwt-expiry`
- `refactor/125-payment-split`
- `docs/126-readme-update`
- `hotfix/127-prod-crash`

---

## 3. 기본 작업 흐름

1. **이슈 생성** — `.github/ISSUE_TEMPLATE/` 양식 선택
2. **브랜치 생성** — `develop` 최신 상태에서 분기
   ```sh
   git checkout develop
   git pull
   git checkout -b feat/123-login-api
   ```
3. **작업 + 커밋** — [커밋 컨벤션](commit-convention.md) 준수
4. **PR 생성** — base: `develop`
5. **Squash and merge**
6. **원본 브랜치 삭제** (GitHub 머지 후 자동 삭제 옵션 권장)

---

## 4. PR 정책

- **머지 전략**: Squash and merge
- **PR 제목**: 머지 시 커밋 subject가 됨 → [커밋 컨벤션](commit-convention.md) 형식으로 작성
  - 예: `✨ feat: 로그인 API 엔드포인트 추가`
- **PR 본문**: `.github/pull_request_template.md` 따름
- **CI 통과 필수**: PR 생성 시 `.github/workflows/ci.yml` 의 Build & Test 잡이 자동 실행 (`./gradlew build` = 컴파일 + Spotless + 테스트 + Jacoco)
- 머지 후 원본 브랜치 삭제

---

## 5. Hotfix 흐름

운영 장애 등 긴급 수정 시:

1. `main` 에서 `hotfix/<이슈번호>-<설명>` 분기
2. 작업 후 `main` 으로 PR + Squash merge
3. **`develop` 에 동기화** — hotfix가 누락되지 않도록 반드시 반영
   ```sh
   git checkout develop
   git pull
   git merge main
   git push
   ```
