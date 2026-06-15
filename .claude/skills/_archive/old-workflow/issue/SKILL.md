---
name: issue
description: GitHub Issue 를 생성한다. type (feat / fix / refactor / docs / chore) 을 먼저 정하고, 해당 템플릿을 사용자와 대화로 채워 gh CLI 로 이슈를 만든다. 정책·scope 결정 단계 — 임의 결정 금지.
---

# /issue — GitHub Issue 생성

마감픽 워크플로우 1단계. 정책 / scope 를 사용자와 함께 확정해 GitHub Issue 로 박는다.

> 임의 결정 금지. 미정 사항 발견 시 옵션 제시 후 사용자 확정 ([CLAUDE.md 의사결정 룰](../../../CLAUDE.md)).

## 입력
- `{기능명}` — 한국어 자유 형식 (예: `매장 등록 신청`)
- 없으면 사용자에게 물음

## 흐름

### 1. Type 결정 + 사전 점검

**Type 결정** (먼저 — 이후 단계의 템플릿 / 라벨 / emoji / 후속 워크플로우를 좌우):

| Type | 템플릿 | 후속 워크플로우 |
|---|---|---|
| `feat` | [`feature.md`](../../../.github/ISSUE_TEMPLATE/feature.md) | `/issue` → `/impl` (plan mode → 코드 → 머지) |
| `fix` | [`fix.md`](../../../.github/ISSUE_TEMPLATE/fix.md) | `/issue` → `/impl` (plan mode → 코드 → 머지) |
| `refactor` | [`refactor.md`](../../../.github/ISSUE_TEMPLATE/refactor.md) | `/issue` → `/impl` (해당 단계만) |
| `docs` / `chore` | [`docs.md`](../../../.github/ISSUE_TEMPLATE/docs.md) | `/issue` → `/impl` (파일 편집 → 빌드 sanity → 머지) |

> **`/spec` 은 옵트인**: handoff (다른 세션 / 모델 / 외주 / 다중 stakeholder 사전 리뷰) 가 필요할 때만 사용자가 명시 호출. 자동 흐름엔 없음.

판단:
- 코드 동작 추가 / 변경 → `feat`
- 버그 수정 → `fix`
- 코드 구조 / 가독성 / 성능 개선 (동작 동일) → `refactor`
- 문서 / 컨벤션 / 워크플로우 / 빌드 / 인프라 → `docs` (`chore` 도 이 양식 사용)

사용자에게 type 확인 (기본 = `feat`).

**사전 점검 (Read only)** — type 별:
- `feat` / `fix`: [`features.md`](../../../docs/features.md) / [`product.md`](../../../docs/product.md) / [`glossary.md`](../../../docs/glossary.md) / [`policy.md`](../../../docs/policy.md) / [`erd/overview.md`](../../../docs/erd/overview.md)
- `refactor` / `docs` / `chore`: 변경 대상 파일 / 관련 SKILL / 관련 convention 문서

scope 밖이면 → 사용자에게 알리고 `product.md` / `features.md` 갱신 여부 논의 (`feat` / `fix` 만 해당).

### 2. 본문 작성 (대화)

선택한 type 의 템플릿을 따른다. **한 섹션씩 채우고 사용자 검토 → 다음 섹션** 흐름.

#### Type = feat → [`feature.md`](../../../.github/ISSUE_TEMPLATE/feature.md) (4섹션)

1. **Context** — 왜 만드는가 / 비즈니스 맥락
2. **Scope** — In Scope / Out of Scope (이 이슈 범위 내 / 외)
3. **핵심 정책 결정** — `policy.md` / `product.md` 의 미정 사항 + **영향도 높은 결정**. /impl 의 plan mode 합의 기준점이 됨. 다음 체크리스트로 누락 점검:
   - **다중성 / 카디널리티** — 1:1 인지 1:N 인지, 컬렉션 필드면 다중 선택인지 단일인지. 예: "카테고리 다중 선택? 단일?"
   - **권한 분기** — role 별 동작 차이 (소비자 / 셀러 / 어드민)
   - **인덱스 / 유니크 영향** — 새 검색 패턴, 유니크 제약 (이메일 / 닉네임 등)
   - **마이그레이션 영향** — 기존 테이블 NOT NULL 컬럼 추가, FK 변경 등
   - **Enum 후보 / 상태값** — 가능한 값 전체. 후보 누락은 마이그레이션 / 분기 손실로 이어짐
   - **외부 시스템 의존** — 외부 API / 이메일 / 알림 발송 등 (출시 전 단계 Mock 여부 포함)

   > 위 항목 중 **`features.md` / `policy.md` 기술이 명확하지 않거나 충돌하는 부분**은 임의 가정 금지. 옵션을 명시적으로 제시하고 사용자 확정 (AGENTS.md 의사결정 룰).
4. **Business Logic (큰 그림)** — 핵심 흐름 요약. 다중 role 흐름은 actor 로 표기 (`셀러: 발행 → 소비자: 사용`). **상세 설계는 `/impl` 의 plan mode 에서**

#### Type = fix → [`fix.md`](../../../.github/ISSUE_TEMPLATE/fix.md)

1. **현상** — 무엇이 잘못되었는지
2. **재현 방법** — 단계 / 입력
3. **예상 동작** — 원래 어떻게 동작해야 하는지
4. **참고** — 스크린샷 / 로그 / 환경 (해당 시)

#### Type = refactor → [`refactor.md`](../../../.github/ISSUE_TEMPLATE/refactor.md)

1. **현재 문제** — 무엇이 아쉬운가
2. **변경 방향** — 어떻게 바꾸려고 하는가
3. **기대 효과** — 무엇이 좋아지는가

#### Type = docs / chore → [`docs.md`](../../../.github/ISSUE_TEMPLATE/docs.md) (4섹션)

1. **Context** — 왜 바꾸는가 / 배경 / 트리거 / 발견 경위
2. **Changes** — 어떤 파일 / 섹션이 어떻게 바뀌나 (high-level — 정확한 diff 는 PR)
3. **Out of Scope** — 같이 바꾸지 않는 것
4. **영향 / 후속** (해당 시) — 다른 docs 동기화 / 1회 셋업 / 머지 후 작업 / 다른 슬롯 적용

원칙:
- **섹션마다 검토** — 한 섹션 채우면 사용자에게 보여주고 OK 받은 후 다음 섹션
- 미정 발견 → 옵션 제시 + 사용자 결정 받기
- 정책 결정이 docs 갱신을 부르면 함께 수정 제안

### 3. 관련 docs 갱신 (필요 시)

이 단계에서 갱신 OK 한 docs (CLAUDE.md 워크플로우 참조):
- `product.md` / `features.md` / `policy.md` / `glossary.md`

전역 코딩 컨벤션 (api-convention / coding-convention / test-convention / commit-convention / git-workflow) 은 **별도 이슈**. 발견 시 사용자에게 알리고 메모만.

### 4. 라벨 결정

- **type 라벨**: §1 에서 결정한 type 그대로 (`feat` / `fix` / `refactor` / `docs` / `chore`)
- **domain 라벨**: `feat` / `fix` 만 해당 — 다음 중 1개 ([ERD overview](../../../docs/erd/overview.md) 그룹):
  - `users`, `stores`, `products`, `orders`, `payments`, `reviews`, `notifications`, `benefits`, `operations`, `statistics`
  - `refactor` / `docs` / `chore` 는 domain 라벨 생략 (워크플로우 / 메타 변경은 도메인 무관)
- 라벨은 GitHub 에 이미 생성되어 있다고 가정 (없으면 사용자에게 알리고 중단)

### 5. 검토 (사용자 승인 단계 ★)

본문 전체 + 라벨 + 제목을 **메시지로 출력**해서 사용자에게 보여준다. 사용자 OK 받기 전까지 **절대 `gh issue create` 호출 X**.

수정 요청 받으면 해당 섹션만 수정 후 다시 보여줌.

### 6. 이슈 생성

PowerShell here-string 으로 본문 전달 (한글 / 멀티라인 호환):

```powershell
gh issue create `
  --title "<emoji> <type>: {기능명}" `
  --body @'
{...본문...}
'@ `
  --label "<type>[,<domain>]"
```

제목 형식 — type 별 emoji ([commit-convention 일치](../../../docs/commit-convention.md)):
- `✨ feat:` / `🐛 fix:` / `♻️ refactor:` / `📝 docs:` / `🔧 chore:`
- subject 50자 이내, 마침표 X, 명사형 종결 ([commit-convention §4](../../../docs/commit-convention.md))

### 7. 작업 브랜치 + 슬롯 attach

이슈 생성 직후, origin 에 브랜치를 만들고 비어 있는 슬롯에 attach 한다 (메인 디렉터리에서 실행). 이후 `/spec`·`/impl` 은 그 슬롯 안에서 진행.

**슬러그 추출**:
1. 이슈 제목에서 type prefix 제거 (`^[이모지] [type]: ` 패턴)
2. 남은 한국어 기능명을 [`glossary.md`](../../../docs/glossary.md) 영문 매핑으로 변환
3. 영문 단어 kebab-case 로 결합 — 예: `매장 등록 신청` → `store-registration`

glossary 에 없는 용어는 사용자에게 옵션 제시 + 확정. 추출 결과는 **사용자에게 확인 후 사용**.

**빈 슬롯 찾기**:
```powershell
git worktree list
```
`(detached HEAD)` 로 표시된 슬롯이 빈 슬롯. 기본 슬롯 풀은 `magampick-api-wt1/wt2/wt3` ([AGENTS.md §"병렬 운영"](../../../AGENTS.md)). 모두 점유 중이면 사용자에게 슬롯 정리 안내 후 중단 (또는 임시 슬롯 추가 여부 확인).

**브랜치 생성 + 슬롯 attach** (슬러그 + 빈 슬롯 확정 후):
```powershell
$gh = 'C:\Program Files\GitHub CLI\gh.exe'
& $gh issue develop {N} --repo MagamPick/magampick-api --base develop --name "feat/{N}-{슬러그}"
git -C ../magampick-api-wtX switch "feat/{N}-{슬러그}"
```
- `gh issue develop` — GitHub 이슈에 연결된 브랜치를 origin 에 생성 (PR 머지 시 이슈 자동 클로즈). `--checkout` 안 함 — 메인 디렉터리는 `develop` 고정
- `git -C ../magampick-api-wtX switch` — 선택한 빈 슬롯에 그 브랜치 attach (`wtX` 는 실제 빈 슬롯 번호로 치환)
- type 이 feat 가 아니면 prefix 조정 (`fix/`, `refactor/`, `docs/` 등)

### 8. 결과 보고

생성된 이슈 번호 + URL + attach 된 슬롯 경로를 사용자에게 알림. 다음 단계 안내:

> ✅ 이슈 #{N} 생성 + 슬롯 attach: `../magampick-api-wtX`
> 그 디렉터리에서 에이전트를 새로 띄운 뒤 `/impl {N}` 진행:
> ```
> cd ../magampick-api-wtX
> claude   # 또는 codex
> /impl {N}
> ```

> **Claude Code 한정 편의**: 같은 세션을 이어가고 싶으면 위 relaunch 대신 `EnterWorktree` 로 슬롯에 진입해도 된다 (세션 앵커가 슬롯으로 이동). Codex 에는 없는 기능이라 canonical 절차는 relaunch 기준.
>
> **handoff 가 필요한 경우** (다른 모델 / 외주 / 사전 리뷰): `/impl` 대신 `/spec {N}` 을 먼저 호출해서 spec 파일을 만들고, 그 후 `/impl` 로 진행.

## 에러 처리

| 상황 | 처리 |
|---|---|
| `gh --version` 실패 | gh CLI 미설치 → `winget install GitHub.cli` 안내 후 중단 |
| `gh auth status` 실패 | `gh auth login` 안내 후 중단 |
| `gh repo view` 실패 | 리포지토리 정보 확인 / 사용자에게 위임 |
| 라벨 없음 | 사용자에게 알리고 중단 (라벨은 사전 생성되어 있어야 함) |

## 주의

- **임의 결정 X** — 정책 / scope 미정 사항은 항상 사용자 확정. `features.md` / `policy.md` 와 충돌하는 가정 발견 시도 옵션으로 던지기
- **사용자 검토 없이 이슈 생성 X** — 6번 단계 강제
- **상세 설계는 `/impl` 의 plan mode 에서** — 이슈는 정책 / scope / 영향도 큰 결정 / 큰 그림까지. handoff 가 필요한 케이스 (다른 세션 / 모델 / 외주) 만 `/spec` 옵트인
- **worktree 부트스트랩 (7~8번 단계)** — 이슈 생성 후 브랜치+worktree 까지 만들고 사용자를 worktree 로 보낸다. 슬러그 추출 실패(glossary 미정) 시 사용자 확정 받기
- **PowerShell 5.1 호환**: gh 본문은 here-string `@'...'@` 사용 (한글 인코딩 안전). PowerShell 인자 파싱이 here-string 멀티라인을 깨뜨릴 수 있으면 임시 파일 + `--body-file` 로 우회
