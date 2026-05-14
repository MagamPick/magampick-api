# CLAUDE.md

@AGENTS.md

---

## Claude Code Dispatch

- `/issue`, `/spec`, `/impl` 워크플로우는 `.claude/skills/` 아래의 Claude Skill 을 따른다.
- `/issue`, `/spec`, 사용자 대화 / 결정은 기본 모델인 Opus 에 적합하다.
- `/impl` 구현은 시작 전에 Sonnet 전환 또는 Sonnet Agent 위임을 검토한다.
- Agent 위임 시 가능하면 `isolation=worktree` 를 사용하고, 동시 Agent 는 1~2개 정도로 제한한다.
- Claude Code 전용 설정과 권한은 `.claude/settings.json` 을 따른다.

## Claude Code Skills

| 명령 | Skill |
|---|---|
| `/issue {기능명}` | `.claude/skills/issue/SKILL.md` |
| `/spec {이슈번호}` | `.claude/skills/spec/SKILL.md` |
| `/impl {이슈번호}` | `.claude/skills/impl/SKILL.md` |

Claude Skill 내부의 상세 절차가 `AGENTS.md` 의 공통 규칙보다 구체적이면, 해당 Skill 을 우선 적용한다.
