---
name: issue
description: 새 기능에 대한 GitHub Issue 를 생성한다. .github/ISSUE_TEMPLATE/feature.md 의 5섹션을 사용자와 대화로 채우고 gh CLI 로 이슈를 만든다. 정책·scope 결정 단계 — 임의 결정 금지.
---

# /issue — GitHub Issue 생성

마감픽 워크플로우 1단계. 정책 / scope 를 사용자와 함께 확정해 GitHub Issue 로 박는다.

> 임의 결정 금지. 미정 사항 발견 시 옵션 제시 후 사용자 확정 ([CLAUDE.md 의사결정 룰](../../../CLAUDE.md)).

## 입력
- `{기능명}` — 한국어 자유 형식 (예: `매장 등록 신청`)
- 없으면 사용자에게 물음

## 흐름

### 1. 사전 점검 (Read only)
다음 docs 에서 해당 기능 컨텍스트 확인:
- [`features.md`](../../../docs/features.md) — scope 안인가
- [`product.md`](../../../docs/product.md) — Out of Scope 와 충돌 없나
- [`glossary.md`](../../../docs/glossary.md) — 도메인 용어
- [`policy.md`](../../../docs/policy.md) — 정책 영향
- [`erd/overview.md`](../../../docs/erd/overview.md) — 도메인 그룹 (라벨 결정용)

scope 밖이면 → 사용자에게 알리고 `product.md` / `features.md` 갱신 여부 논의.

### 2. 5섹션 본문 작성 (대화)

[`feature.md` 템플릿](../../../.github/ISSUE_TEMPLATE/feature.md) 따름. **한 섹션씩 채우고 사용자 검토 → 다음 섹션** 흐름:

1. **Context** — 왜 만드는가 / 비즈니스 맥락
2. **Scope** — In Scope / Out of Scope (이 이슈 범위 내 / 외)
3. **User Roles** (해당 시) — Customer / Seller / Admin
4. **핵심 정책 결정** (해당 시) — `policy.md` / `product.md` 의 미정 사항 중 이 기능에 필요한 결정
5. **Business Logic (큰 그림)** — 핵심 흐름 요약. **상세는 `/spec` 에서**

원칙:
- **섹션마다 검토** — 한 섹션 채우면 사용자에게 보여주고 OK 받은 후 다음 섹션
- 미정 발견 → 옵션 제시 + 사용자 결정 받기
- 정책 결정이 docs 갱신을 부르면 함께 수정 제안

### 3. 관련 docs 갱신 (필요 시)

이 단계에서 갱신 OK 한 docs (CLAUDE.md 워크플로우 참조):
- `product.md` / `features.md` / `policy.md` / `glossary.md`

전역 코딩 컨벤션 (api-convention / coding-convention / test-convention / commit-convention / git-workflow) 은 **별도 이슈**. 발견 시 사용자에게 알리고 메모만.

### 4. 라벨 결정

- **type 라벨**: 기본 `feat`. 버그면 `fix` 등 — 사용자에게 확인
- **domain 라벨**: 다음 중 해당 도메인 1개 선택 ([ERD overview](../../../docs/erd/overview.md) 그룹)
  - `users`, `stores`, `products`, `orders`, `payments`, `reviews`, `notifications`, `benefits`, `operations`, `statistics`
- 라벨은 GitHub 에 이미 생성되어 있다고 가정 (없으면 사용자에게 알리고 중단)

### 5. 검토 (사용자 승인 단계 ★)

본문 전체 + 라벨 + 제목을 **메시지로 출력**해서 사용자에게 보여준다. 사용자 OK 받기 전까지 **절대 `gh issue create` 호출 X**.

수정 요청 받으면 해당 섹션만 수정 후 다시 보여줌.

### 6. 이슈 생성

PowerShell here-string 으로 본문 전달 (한글 / 멀티라인 호환):

```powershell
gh issue create `
  --title "✨ feat: {기능명}" `
  --body @'
{...본문...}
'@ `
  --label "feat,{domain}"
```

제목 형식: `✨ feat: {기능명}` ([commit-convention 일치](../../../docs/commit-convention.md))
- 다른 type 면 해당 이모지 / type 으로 (`🐛 fix:` 등)
- subject 50자 이내, 마침표 X, 명사형 종결 ([commit-convention §4](../../../docs/commit-convention.md))

### 7. 작업 브랜치 + worktree 생성

이슈 생성 직후, 작업할 worktree 를 만든다 (주 디렉터리에서 실행). 이후 `/spec`·`/impl` 은 이 worktree 안에서 진행.

**슬러그 추출**:
1. 이슈 제목에서 type prefix 제거 (`^[이모지] [type]: ` 패턴)
2. 남은 한국어 기능명을 [`glossary.md`](../../../docs/glossary.md) 영문 매핑으로 변환
3. 영문 단어 kebab-case 로 결합 — 예: `매장 등록 신청` → `store-registration`

glossary 에 없는 용어는 사용자에게 옵션 제시 + 확정. 추출 결과는 **사용자에게 확인 후 사용**.

**브랜치 + worktree 생성** (슬러그 확정 후):
```powershell
$gh = 'C:\Program Files\GitHub CLI\gh.exe'
& $gh issue develop {N} --repo MagamPick/magampick-api --base develop --name "feat/{N}-{슬러그}"
git worktree add ../magampick-api-{N}-{슬러그} "feat/{N}-{슬러그}"
```
- `gh issue develop` — GitHub 이슈에 연결된 브랜치를 origin 에 생성 (PR 머지 시 이슈 자동 클로즈). `--checkout` 안 함 — 주 디렉터리는 `develop` 고정
- `git worktree add` — 그 브랜치를 sibling 디렉터리에 checkout
- type 이 feat 가 아니면 prefix 조정 (`fix/`, `refactor/` 등)

### 8. 결과 보고

생성된 이슈 번호 + URL + worktree 경로를 사용자에게 알림. 다음 단계 안내:

> ✅ 이슈 #{N} 생성 + worktree 준비됨: `../magampick-api-{N}-{슬러그}`
> 그 디렉터리에서 에이전트를 새로 띄운 뒤 `/spec {N}` 진행:
> ```
> cd ../magampick-api-{N}-{슬러그}
> claude   # 또는 codex
> /spec {N}
> ```

> **Claude Code 한정 편의**: 같은 세션을 이어가고 싶으면 위 relaunch 대신 `EnterWorktree` 로 worktree 에 진입해도 된다 (세션 앵커가 worktree 로 이동). Codex 에는 없는 기능이라 canonical 절차는 relaunch 기준.

## 에러 처리

| 상황 | 처리 |
|---|---|
| `gh --version` 실패 | gh CLI 미설치 → `winget install GitHub.cli` 안내 후 중단 |
| `gh auth status` 실패 | `gh auth login` 안내 후 중단 |
| `gh repo view` 실패 | 리포지토리 정보 확인 / 사용자에게 위임 |
| 라벨 없음 | 사용자에게 알리고 중단 (라벨은 사전 생성되어 있어야 함) |

## 주의

- **임의 결정 X** — 정책 / scope 미정 사항은 항상 사용자 확정
- **사용자 검토 없이 이슈 생성 X** — 6번 단계 강제
- **상세 명세는 `/spec` 으로** — 이슈는 정책 / scope / 큰 그림까지만
- **worktree 부트스트랩 (7~8번 단계)** — 이슈 생성 후 브랜치+worktree 까지 만들고 사용자를 worktree 로 보낸다. 슬러그 추출 실패(glossary 미정) 시 사용자 확정 받기
- **PowerShell 5.1 호환**: gh 본문은 here-string `@'...'@` 사용 (한글 인코딩 안전)
