---
name: magampick-workflow
description: MagamPick API workflow skill for creating GitHub issues, writing implementation specs, and implementing specs. Use when the user asks for /issue, /spec, /impl, issue creation from a feature idea, docs/specs generation from a GitHub Issue, or spec-based backend implementation in the magampick-api repository.
---

# MagamPick Workflow

Use this skill for the MagamPick API repository's four-step feature workflow:

1. `/issue {feature}`: decide policy and scope, then create a GitHub Issue.
2. `/spec {issue-number}`: turn an approved issue into `docs/specs/{N}-{slug}.md`.
3. `/impl {issue-number}`: implement from the spec with tests and build verification.
4. Merge/PR: follow `docs/commit-convention.md` and `docs/git-workflow.md`; always confirm commit messages and PR creation with the user first.

## Required Context

Before running a workflow, read the repository root `AGENTS.md`. It is the single source of truth for shared project rules across Codex and Claude Code.

For workflow details, load only the reference file needed for the user's request:

- `/issue` or feature issue creation: read `references/issue.md`.
- `/spec` or implementation spec creation: read `references/spec.md`.
- `/impl` or spec-based implementation: read `references/impl.md`.

## Hard Rules

- Speak with the user in Korean.
- Do not make product, policy, scope, convention, or structure decisions without user confirmation.
- Do not create GitHub issues, save spec files, commit, push, create PRs, or merge before showing the exact content and receiving user approval.
- Run `/spec` and `/impl` from inside the issue's git worktree directory (`../magampick-api-{N}-{slug}`), which `/issue` creates. Never work in the main directory on `main` or `develop` for `/spec` or `/impl`.
- Do not modify already-merged migration files. Add a new timestamped migration instead.
- Preserve `.claude/` files. They are still used by Claude Code.

## Tool Notes

- Prefer PowerShell-compatible commands in this Windows workspace.
- Use `gh --repo MagamPick/magampick-api` for GitHub CLI commands when practical.
- If a command needs network or writes outside the workspace, request approval through the normal Codex escalation path.
- Run `./gradlew spotlessApply` after code edits and `./gradlew build` before reporting `/impl` complete, unless blocked.
