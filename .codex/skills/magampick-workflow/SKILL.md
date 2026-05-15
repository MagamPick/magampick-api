---
name: magampick-workflow
description: MagamPick API workflow skill for creating GitHub issues, writing implementation specs, and implementing specs. Use when the user asks for /issue, /spec, /impl, issue creation from a feature idea, docs/specs generation from a GitHub Issue, or spec-based backend implementation in the magampick-api repository.
---

# MagamPick Workflow

Use this skill for the MagamPick API repository's four-step feature workflow:

1. `/issue {feature}`: decide policy and scope, then create a GitHub Issue.
2. `/spec {issue-number}`: turn an approved issue into `docs/specs/{N}-{slug}.md`.
3. `/impl {issue-number}`: implement from the spec with tests and build verification.
4. Merge: continues from `/impl` in the same session. After build passes, confirm commit message + PR body with the user, then commit → push → create PR → watch CI → on CI green, merge automatically (no extra user prompt — CI is the merge gate per `docs/git-workflow.md §4`) → clean up the slot → pull `develop` in the main directory → report cycle complete. On CI red, report the failure cause and wait for user direction.

## Required Context

Before running a workflow, read the repository root `AGENTS.md`. It is the single source of truth for shared project rules across Codex and Claude Code.

For workflow details, load only the reference file needed for the user's request:

- `/issue` or feature issue creation: read `references/issue.md`.
- `/spec` or implementation spec creation: read `references/spec.md`.
- `/impl` or spec-based implementation: read `references/impl.md`.

## Hard Rules

- Speak with the user in Korean.
- Do not make product, policy, scope, convention, or structure decisions without user confirmation.
- Do not create GitHub issues, save spec files, or push code before showing the exact content and receiving user approval. For commits and PR creation, show the message/body and get explicit approval first; after the user approves the PR body, the rest of the merge cycle (CI watch → auto-merge on green → slot cleanup → develop pull) proceeds in the same session without further prompts, per `docs/git-workflow.md §4`.
- Run `/spec` and `/impl` from inside the slot where the issue's branch is attached (`../magampick-api-wtX`, attached by `/issue`). Never work in the main directory on `main` or `develop` for `/spec` or `/impl`. See `AGENTS.md` §"병렬 운영" for the slot pool model.
- Do not modify already-merged migration files. Add a new timestamped migration instead.
- Preserve `.claude/` files. They are still used by Claude Code.

## Tool Notes

- Prefer PowerShell-compatible commands in this Windows workspace.
- Use `gh --repo MagamPick/magampick-api` for GitHub CLI commands when practical.
- If a command needs network or writes outside the workspace, request approval through the normal Codex escalation path.
- Run `./gradlew spotlessApply` after code edits and `./gradlew build` before reporting `/impl` complete, unless blocked.
