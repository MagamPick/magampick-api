---
name: magampick-workflow
description: MagamPick API workflow skill for implementing features from a Notion specification page. Use when the user asks for /impl, or asks to implement / build / write code for a feature backed by a Notion page in the magampick-api repository. The Notion "기능 명세 (Features)" database is the single source of truth for policy / scope / domain decisions. GitHub Issues are NOT used in this workflow.
---

# MagamPick Workflow

Use this skill for the MagamPick API repository's Notion-driven feature workflow:

1. **Spec** (out of band): the user writes the feature specification as a page in the Notion "기능 명세 (Features)" database. (Authored separately, sometimes with help in `../magampick_docs`, but Notion is the canonical source.)
2. `/impl <notion-url>`: fetch the Notion page → enter plan mode → agree with the user → flip Notion status `기획` → `개발중` → attach a slot → TDD red→green implementation → build → external review → merge.
3. **Merge cycle** (same session as `/impl`): after build passes, confirm commit message + PR body with the user, then commit → push → create PR → watch CI → on CI green, auto-merge (no extra user prompt — CI is the merge gate per `docs/git-workflow.md §4`) → clean up the slot → pull `develop` in the main directory → update the Notion page status `개발중` → `운영중` (or check off the body checklist item if the Notion page is split into multiple PRs).

GitHub Issues are not created in this workflow. The Notion page replaces the issue. Tracking happens via the PR + the body checklist on the Notion page.

## Required Context

Before running the workflow, read the repository root `AGENTS.md`. It is the single source of truth for shared project rules across Codex and Claude Code.

For workflow details, load only the reference file needed for the user's request:

- `/impl` or Notion-driven implementation: read `references/impl.md`.

The old issue / spec references have been archived to `.codex/skills/_archive/old-workflow/references/` and are not part of the active workflow.

## Hard Rules

- Speak with the user in Korean.
- Do not make product, policy, scope, convention, or structure decisions without user confirmation. Impactful decisions (cardinality, role-based branching, index/unique impact, migration impact, enum candidates, external system dependency) must be raised as options in `/impl`'s plan mode. Confirmed decisions are **written back into the Notion page body** so they survive the session.
- Do not save files, commit, or push code before showing the exact content and receiving user approval. For commits and PR creation, show the message/body and get explicit approval first; after the user approves the PR body, the rest of the merge cycle (CI watch → auto-merge on green → slot cleanup → develop pull → Notion status update) proceeds in the same session without further prompts, per `docs/git-workflow.md §4`.
- Run `/impl`'s code phase from inside the slot where the feature branch is attached (`../magampick-api-wtX`). The bootstrap phase (Notion fetch + plan + slot attach) may run from the main directory; the execution phase (code work + merge) must run inside the slot. Never work in the main directory on `main` or `develop` for code edits. See `AGENTS.md` §"병렬 운영" for the slot pool model.
- Do not modify already-merged migration files. Add a new timestamped migration instead.
- Preserve `.claude/` files. They are still used by Claude Code.
- Notion is the single source for policy / scope / domain decisions. Decisions agreed during plan mode must be written back into the Notion page body via `notion-update-page`. Do not rely on in-session memory alone.

## Tool Notes

- Prefer PowerShell-compatible commands in this Windows workspace.
- Use `gh --repo MagamPick/magampick-api` for GitHub CLI commands when practical. Do not call `gh issue ...` for workflow purposes (no issue system).
- Use the Notion MCP tools (`mcp__claude_ai_Notion__notion-fetch`, `mcp__claude_ai_Notion__notion-update-page`) for Notion page reads and status updates. In Codex, use the equivalent Notion MCP tools exposed in the session.
- If a command needs network or writes outside the workspace, request approval through the normal Codex escalation path.
- Run `./gradlew spotlessApply` after code edits and `./gradlew build` before reporting `/impl` complete, unless blocked.
