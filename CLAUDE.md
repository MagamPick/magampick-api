# CLAUDE.md

@AGENTS.md

---

## Claude Code Dispatch

- `/impl <노션URL>` 워크플로우는 `.claude/skills/impl/` 아래의 Claude Skill 을 따른다 (3단계: 노션 명세 → impl → 머지).
- 노션 fetch / plan mode 합의 / 사용자 대화 / 결정은 기본 모델인 Opus 에 적합하다 — `/impl` 모드 A (메인 디렉터리) 가 여기에 해당.
- `/impl` 의 plan 합의 이후 코드 생성 구간 (모드 B — 슬롯에서 TDD 구현) 은 Sonnet 전환 또는 Sonnet Agent 위임을 검토한다.
- `/impl` 의 빌드 통과 후 코드 리뷰는 외부 모델로 받는다 — 디폴트 **Codex 5.5 medium**, 작업 시작 시 토큰 잔량 / 중요도 보고 결정 가능 (Codex 5.5 high / Opus 4.7 / Sonnet 다른 인스턴스 등). 리뷰 결과는 사용자가 검토하고 반영 항목을 선별한 뒤 구현 모델이 적용 — 자동 반영 X.
- Agent 위임 시 가능하면 `isolation=worktree` 를 사용하고, 동시 Agent 는 1~2개 정도로 제한한다.
- Claude Code 전용 설정과 권한은 `.claude/settings.json` 을 따른다.

## Claude Code Skills

| 명령 | Skill | 비고 |
|---|---|---|
| `/impl <노션URL>` | `.claude/skills/impl/SKILL.md` | 워크플로우 2~3단계 (노션 fetch → plan mode → 슬롯 attach → TDD 구현 → 머지 → 노션 상태 갱신) |

> 이전 워크플로우의 `/issue` / `/spec` 스킬은 노션 도입과 함께 archive 됨. 백업: [`.claude/skills/_archive/old-workflow/`](.claude/skills/_archive/old-workflow/).

Claude Skill 내부의 상세 절차가 `AGENTS.md` 의 공통 규칙보다 구체적이면, 해당 Skill 을 우선 적용한다.
