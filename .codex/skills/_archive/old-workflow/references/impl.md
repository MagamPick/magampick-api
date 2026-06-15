# /impl Workflow

Implement code from the issue body + plan-mode agreement + convention docs together. The issue body and plan-mode agreement carry the policy decisions, API contract intent, and domain-specific behavior. The convention docs carry mechanical detail (Swagger annotations, package layout, transaction placement, logging, test enumeration, etc.). Spec files (`docs/specs/{N}-*.md`) are opt-in — read them if present, but their absence does not block. Follow the issue + plan agreement where they speak, fall back to the conventions when they are silent on mechanical detail, and ask the user only when all three are silent on a decision or a complex build/test failure requires interpretation.

## Input

- `{이슈번호}`: required GitHub Issue number.
- Issue body via `gh issue view {N} --repo MagamPick/magampick-api --json title,body,labels` — always the primary input.
- Spec file (optional): `docs/specs/{N}-*.md` — read if present; absence is not blocking.

## 1. Working Directory Guard

`/impl` must run inside the slot where issue #{N}'s branch is attached (`../magampick-api-wtX`, attached by `/issue`). See `AGENTS.md` §"병렬 운영" for the slot pool model.

- If on `<type>/{N}-*` (issue type prefix — `feat` / `fix` / `refactor` / `docs` / `chore`): continue.
- If on `develop` or `main` (the main directory): stop. Report which slot holds issue #{N}'s branch (`git worktree list`) and ask the user to launch the agent there and re-run `/impl {N}`.
- If the branch is not attached to any slot: tell the user to run `/issue` first (or fallback-attach the branch to an empty slot and re-run), then stop.

## 2. Type Branching (Required)

Issue type label decides which steps apply. Check via `gh issue view {N} --json labels` or the `<type>/...` prefix from `git branch --show-current`.

| Step | feat / fix | refactor | docs / chore |
|---|---|---|---|
| §3a Plan Mode Entry + Agreement | ✓ | ✓ | ✓ (light plan — confirm file list) |
| §3b Load Issue + (optional) Spec | ✓ | ✓ | ✓ |
| §4 Migration Version | when needed | when needed | skip |
| §5 Implementation Order steps 1–9 (Entity → Controller) | full | applicable steps only | skip |
| §5 Step 10 (integration test) | golden path only | applicable only | skip |
| §5 Step 11 (`./gradlew spotlessApply`) | ✓ | ✓ | skip (no code change) |
| §5 Step 12 (`./gradlew build`, sanity check) | ✓ | ✓ | ✓ |
| §5 Step 13 (external model review) | ✓ | ✓ | skip (on explicit request only) |
| §5 Step 14 (roadmap update) | ✓ | when applicable | when applicable |
| §9 Completion report + merge cycle | ✓ | ✓ | ✓ |

- **`docs` / `chore` flow**: skip code steps. Work = §3a light plan (confirm files / scope) → edit the files listed in the issue's `Changes` section → `./gradlew build` (sanity check) → roadmap update (when applicable) → merge cycle. (External review at Step 13 only on explicit request.)
- **`refactor` flow**: §3a plan mode agrees on policy / API change impact. If a heavy handoff is genuinely needed, the user invokes `/spec {N}` explicitly first (rare).

## 3a. Plan Mode Entry + Agreement (required for all types)

Before any code or file edits, enter plan mode and agree with the user. Plan mode plays the in-session, ephemeral version of the "decision review before implementation" role that a spec would play in a handoff scenario. (Claude Code: native plan mode via shift+tab. Codex: emit a structured plan in chat and wait for explicit user approval before any edit.)

**Inputs to gather inside plan mode**:

1. **Load issue body** — `gh issue view {N} --repo MagamPick/magampick-api --json title,body,labels`. The issue's Context / Scope / Core Policy Decisions / Business Logic (or, for docs, Changes) is the primary plan input.
2. **Spec file lookup (optional)** — if `docs/specs/{N}-*.md` matches, read it and fold it into the plan. Absence is not blocking.
3. **Convention pre-check** — for feat/fix, mentally hold the convention sections that will be touched.

**Plan content** (scaled to type):

- Affected endpoints / entities / migrations.
- Impactful decisions only when not already explicit in the issue — surface as options:
  - Cardinality (1:1 vs 1:N, single vs multi-select)
  - Role-based branching
  - Index / unique impact
  - Migration impact (NOT NULL additions, etc.)
  - Enum / state value candidates
  - External system dependency (Mock vs real integration)
- Applicable steps (from the Type Branching table — where to start, where to stop)
- (docs/chore) target file list

**Agreement rules**:

- Impactful decisions and assumptions that conflict with `features.md` / `policy.md` must be surfaced as options — do not assume. Use the agent's native question/option mechanism (Claude Code: AskUserQuestion; Codex: native chat prompts) — do not hardcode tool names in the plan; just behave the right way.
- Decisions already clearly pinned in the issue body / spec do not need to reappear in the plan (avoid double-review).
- Plan agreement = "proceed this way". After exiting plan mode, run §3b → §5.

## 3b. Load Issue + (optional) Spec

Already loaded inside §3a; this step is the explicit re-affirmation:

- Issue body (Context / Scope / Policy Decisions / Business Logic) — always primary source.
- Spec file (`docs/specs/{이슈번호}-*.md`) — use together when present. If multiple specs match, ask the user to choose. Zero matches is normal (opt-in).

Spec sections to apply when present:

- Context / Scope
- API Specification (field / type / constraint / error-code tables — Swagger annotation bodies are not in the spec; derive them via `docs/api-convention.md` §12)
- Data Model
- Business Logic (Validation / Error / Edge cases — the standard processing flow and standard test cases are not enumerated in the spec; derive them from conventions + the standard flow)
- External Dependencies
- Implementation Notes (only decisions that depart from the conventions — apply as written)

### Convention as Single Source When Issue / Plan / Spec Are Silent

The issue and spec are intentionally policy-and-contract-focused. When they do not specify a mechanical detail, do not invent one — pull it from the convention docs and apply it consistently:

| Topic | Source |
|---|---|
| Swagger / OpenAPI annotation placement and content | `docs/api-convention.md` §12 |
| Package / layer / `@Transactional` placement / exceptions / logging / MapStruct | `docs/coding-convention.md` §1-4, §8, §9, §11 |
| Test kinds / depth / fixtures / Korean test method names | `docs/test-convention.md` |
| Authentication / authorization / self-resource access | `docs/auth.md` |
| Migration format / Enum CHECK / Point / KST | `docs/erd/overview.md` |
| Standard processing flow (JWT → repository.findById → 404 → dirty checking → mapper) | Apply as the default — no narration needed |

Ask the user only when issue + plan + (optional) spec + convention are all silent. Most such cases should already be resolved in §3a plan agreement.

## 4. Migration Version

Use the current timestamp:

```text
V{YYYYMMDDHHMMSS}__{description}.sql
```

If a timestamp collision occurs, add one second. Never edit already-merged migration files.

## 5. Implementation Order

Follow this order unless the existing codebase makes a small local adjustment necessary:

1. Entity
2. Migration SQL
3. ERD table doc
4. Repository
5. Service unit tests (written alongside the service, not strictly before)
6. Service implementation
7. DTOs and MapStruct mapper
8. Controller `@WebMvcTest` (written alongside the controller, not strictly before)
9. Controller implementation
10. Integration test when the feature is a golden-path flow (signup / order / payment / refund) — see §6. Skip otherwise.
11. `./gradlew spotlessApply`
12. `./gradlew build`
13. External model review (read-only consultation)
14. Update `docs/roadmap.md`

Run the external model review only after the build passes (Step 13):

- Model default: **Codex 5.5 medium**. The user may pick a different model at the start (Codex 5.5 high / Opus 4.7 / another Sonnet instance) based on remaining tokens and work importance. Prefer a model from a different family than the implementer (different blindspots).
- Read-only consultation — no separate worktree needed.
- Invocation: agent-appropriate mechanism (Claude Code: Agent tool or headless `claude -p ...`; Codex: new session; etc.).
- Prompt — ask the reviewer to cover all 8 categories, critiquing case-fit even when conventions are ticked off:
  1. Intent alignment with issue + plan agreement (no over-engineering)
  2. Encapsulation / OO — Tell-Don't-Ask, predicate naming (state vs capability), SRP, creation pattern consistency
  3. Spring Boot practices — `@Transactional` boundaries, exception handling, multipart config, MapStruct, `@PageableDefault`, validation
  4. Security — auth matchers, input validation, privilege bypass, sensitive data exposure
  5. Performance — N+1, fetch strategy, missing pagination
  6. Convention adherence — fit for case, consistency in silent areas, better alternatives
  7. API / response — HTTP status correctness, response envelope, `@ApiResponses` coverage (incl. 401/403), idempotence
  8. Tests — coverage (happy + edge + authz), assertion meaningfulness, integration test necessity
- Reflection: implementer does NOT auto-apply. The user reads the review, selects items to address, and the implementer (current session) applies the changes. If changes are made, re-run `./gradlew spotlessApply` and `./gradlew build` before proceeding to roadmap.
- Skip for `docs` / `chore` types unless explicitly requested.

Update the roadmap only after the build passes (and external review reflections, if any):

- Find the row for the implemented feature.
- Change status from `미착수` to `완료`.
- Set the `이슈` column to the issue number, for example `#12`.
- Make this edit on the feature branch so it is included in the feature PR and reflected on `develop` only after merge.
- If the feature row does not exist because the approved scope added a new feature, add the row in the appropriate dependency layer.
- If the build failed or implementation stopped, do not mark the roadmap row complete.

## 6. Coding Rules

- Entity path: `src/main/java/{package}/{domain}/entity/{Name}.java`.
- Repository extends `JpaRepository<Entity, Long>`.
- Basic CRUD repositories do not need tests.
- Custom queries need focused `@DataJpaTest`.
- Service tests use Mockito + AssertJ and Korean method names.
- Controller tests use MockMvc + Mockito.
- Use `@WithMockUser` or security context setup when auth is required.
- Integration tests for golden-path flows (signup / order / payment / refund) use `@SpringBootTest @AutoConfigureMockMvc @Transactional` and `extends PostgresTestBase`. They exist to break the AI self-reference risk that mock-heavy unit/slice tests cannot catch — real DB, real service↔repository wiring, real transaction boundary, real security filter. Skip for non-golden-path features.
- DTO request/response shapes follow `docs/api-convention.md`.
- Controllers and DTOs include Springdoc OpenAPI annotations per `docs/api-convention.md` §12 (single source — do not infer placement or wording from the spec).
- Exceptions follow `BusinessException` + `BaseErrorCode` patterns from `docs/coding-convention.md`.

## 7. Docs Allowed During /impl

Allowed:

- `docs/erd/tables/{table}.md`
- `docs/auth.md` when an authentication/authorization policy decision is part of the spec
- `docs/roadmap.md` for the implemented feature row status and issue number

Not allowed without a separate issue:

- `docs/api-convention.md`
- `docs/coding-convention.md`
- `docs/test-convention.md`
- `docs/commit-convention.md`
- `docs/git-workflow.md`

## 8. Verification

Run:

```powershell
./gradlew spotlessApply
./gradlew build
```

If build fails from simple compile/import/format issues, fix and rerun once or twice. If the failure depends on ambiguous business logic or spec interpretation, stop and report the decision needed.

## 9. Completion Report + Merge Cycle

After the build passes, report:

- Created/modified files grouped by Entity, migration, ERD, repository, service/test, DTO/mapper, controller/test, and roadmap.
- Build result.
- Only decisions made when the issue + plan + (optional) spec + conventions were all silent — skip anything that was simply applied per convention or already agreed in §3a plan.
- Remaining blockers, if any.
- Confirm whether `docs/roadmap.md` was updated to `완료` with the issue number, or explain why it was not updated.

Then continue the merge cycle in the same session, per `AGENTS.md` workflow step 3 and `docs/git-workflow.md §4`:

1. **Commit message review** — Subject line only (`<emoji> <type>: <subject>`); no body or footer (see `docs/commit-convention.md` §2). Show the exact commit message and file list to the user; wait for approval. The `commit-msg` hook will reject commits that include a body — do not bypass it with `--no-verify`.
2. **Commit + push.**
3. **PR body review** — Show the PR title and body to the user; wait for approval. This approval also delegates the rest of the merge cycle.
4. **Create the PR** with `gh pr create --base develop ...`.
5. **Watch CI** — Run `gh pr checks {N} --repo MagamPick/magampick-api --watch` in the background. Do not poll or sleep.
6. **Auto-merge on green** — On CI success, immediately run `gh pr merge {N} --merge --delete-branch` without an additional user prompt (CI is the merge gate per `docs/git-workflow.md §4`). Verify with `gh pr view {N} --json state,mergedAt,mergeCommit`.
7. **Slot cleanup + develop pull**:
   ```sh
   git fetch --prune
   git switch --detach origin/develop                # detach current slot
   git branch -D {type}/{N}-{slug}                   # delete local branch
   git -C "{absolute path to main directory}" pull   # update develop in main
   ```
   The remote branch was already deleted by `--delete-branch`.
8. **Cycle complete report** — PR URL, merge commit, and next-step guidance.

On CI red: report the failure cause and candidate next actions (fix and add a commit, revert, discuss). Do not force-merge or retry merge without user direction.

The merge cycle always completes in the same session, regardless of whether a spec was used (docs / chore / refactor without spec follow the same flow).
