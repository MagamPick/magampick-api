---
name: magampick-workflow
description: MagamPick API workflow skill for creating GitHub issues, implementing from issues with plan mode + convention, and (opt-in) writing implementation specs for handoff scenarios. Use when the user asks for /issue, /impl, /spec, issue creation from a feature idea, plan-mode-driven backend implementation, or spec generation for handoff in the magampick-api repository.
---

# MagamPick Workflow

Use this skill for the MagamPick API repository's three-step feature workflow:

1. `/issue {feature}`: decide policy, scope, and impactful decisions; create a GitHub Issue.
2. `/impl {issue-number}`: enter plan mode → agree with user → implement with tests → build → verification. Issue body + plan agreement + convention docs are the inputs. Spec files are opt-in (used if present).
3. Merge: continues from `/impl` in the same session. After build passes, confirm commit message + PR body with the user, then commit → push → create PR → watch CI → on CI green, merge automatically (no extra user prompt — CI is the merge gate per `docs/git-workflow.md §4`) → clean up the slot → pull `develop` in the main directory → report cycle complete. On CI red, report the failure cause and wait for user direction.

`/spec {issue-number}` is an **opt-in handoff tool** for delegating to another session / model / agent, external contractor, multi-stakeholder review, or genuinely policy-heavy areas (payment / settlement / refund / auth) — it is not part of the default flow.

## Required Context

Before running a workflow, read the repository root `AGENTS.md`. It is the single source of truth for shared project rules across Codex and Claude Code.

For workflow details, load only the reference file needed for the user's request:

- `/issue` or feature issue creation: read `references/issue.md`.
- `/impl` or plan-mode-driven implementation: read `references/impl.md`.
- `/spec` or opt-in handoff spec creation: read `references/spec.md`.

## Hard Rules

- Speak with the user in Korean.
- Do not make product, policy, scope, convention, or structure decisions without user confirmation. Impactful decisions (cardinality, role-based branching, index/unique impact, migration impact, enum candidates, external system dependency) must be raised as options in `/impl`'s plan mode (or `/issue` time).
- Do not create GitHub issues, save spec files, or push code before showing the exact content and receiving user approval. For commits and PR creation, show the message/body and get explicit approval first; after the user approves the PR body, the rest of the merge cycle (CI watch → auto-merge on green → slot cleanup → develop pull) proceeds in the same session without further prompts, per `docs/git-workflow.md §4`.
- Run `/impl` (and `/spec` when opt-in) from inside the slot where the issue's branch is attached (`../magampick-api-wtX`, attached by `/issue`). Never work in the main directory on `main` or `develop` for `/impl` or `/spec`. See `AGENTS.md` §"병렬 운영" for the slot pool model.
- Do not modify already-merged migration files. Add a new timestamped migration instead.
- Preserve `.claude/` files. They are still used by Claude Code.

## Tool Notes

- Prefer PowerShell-compatible commands in this Windows workspace.
- Use `gh --repo MagamPick/magampick-api` for GitHub CLI commands when practical.
- If a command needs network or writes outside the workspace, request approval through the normal Codex escalation path.
- Run `./gradlew spotlessApply` after code edits and `./gradlew build` before reporting `/impl` complete, unless blocked.
