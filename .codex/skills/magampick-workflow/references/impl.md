# /impl Workflow

Implement code from a completed spec and the convention docs together. The spec carries policy decisions, the API contract, and domain-specific behavior. The convention docs carry mechanical detail (Swagger annotations, package layout, transaction placement, logging, test enumeration, etc.). Follow the spec where it speaks, fall back to the conventions when the spec is silent on mechanical detail, and ask the user only when both are silent on a decision or a complex build/test failure requires interpretation.

## Input

- `{이슈번호}`: required GitHub Issue number.
- Find exactly one matching file: `docs/specs/{N}-*.md`.

## 1. Working Directory Guard

`/impl` must run inside the slot where issue #{N}'s branch is attached (`../magampick-api-wtX`, attached by `/issue`). See `AGENTS.md` §"병렬 운영" for the slot pool model.

- If on `feat/{N}-*` (the issue type prefix) and `docs/specs/{N}-*.md` is present: continue.
- If on `develop` or `main` (the main directory): stop. Report which slot holds issue #{N}'s branch (`git worktree list`) and ask the user to launch the agent there and re-run `/impl {N}`.
- If the branch is not attached to any slot: tell the user to run `/issue` or `/spec {N}` first, then stop.

## 2. Load Spec

Read all spec sections:

- Context / Scope
- API Specification (field / type / constraint / error-code tables — Swagger annotation bodies are not in the spec; derive them via `docs/api-convention.md` §12)
- Data Model
- Business Logic (Validation / Error / Edge cases — the standard processing flow and standard test cases are not enumerated in the spec; derive them from conventions + the standard flow)
- External Dependencies
- Implementation Notes (only decisions that depart from the conventions — apply as written)

If no spec exists, tell the user to run `/spec {N}` first. If multiple specs match, ask the user to choose.

### Convention as Single Source When Spec Is Silent

The spec is intentionally thin (see `/spec` reference, §4 Convention-Delegated Areas). When the spec does not specify a mechanical detail, do not invent one — pull it from the convention docs and apply it consistently:

| Topic | Source |
|---|---|
| Swagger / OpenAPI annotation placement and content | `docs/api-convention.md` §12 |
| Package / layer / `@Transactional` placement / exceptions / logging / MapStruct | `docs/coding-convention.md` §1-3, §7, §8, §10 |
| Test kinds / depth / fixtures / Korean test method names | `docs/test-convention.md` |
| Authentication / authorization / self-resource access | `docs/auth.md` |
| Migration format / Enum CHECK / Point / KST | `docs/erd/overview.md` |
| Standard processing flow (JWT → repository.findById → 404 → dirty checking → mapper) | Apply as the default — no spec narration needed |

Ask the user only when both spec and convention are silent.

## 3. Migration Version

Use the current timestamp:

```text
V{YYYYMMDDHHMMSS}__{description}.sql
```

If a timestamp collision occurs, add one second. Never edit already-merged migration files.

## 4. Implementation Order

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
10. Integration test when the feature is a golden-path flow (signup / order / payment / refund) — see §5. Skip otherwise.
11. `./gradlew spotlessApply`
12. `./gradlew build`
13. Update `docs/roadmap.md`

Update the roadmap only after the build passes:

- Find the row for the implemented feature.
- Change status from `미착수` to `완료`.
- Set the `이슈` column to the issue number, for example `#12`.
- Make this edit on the feature branch so it is included in the feature PR and reflected on `develop` only after merge.
- If the feature row does not exist because the approved scope added a new feature, add the row in the appropriate dependency layer.
- If the build failed or implementation stopped, do not mark the roadmap row complete.

## 5. Coding Rules

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

## 6. Docs Allowed During /impl

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

## 7. Verification

Run:

```powershell
./gradlew spotlessApply
./gradlew build
```

If build fails from simple compile/import/format issues, fix and rerun once or twice. If the failure depends on ambiguous business logic or spec interpretation, stop and report the decision needed.

## 8. Completion Report + Merge Cycle

After the build passes, report:

- Created/modified files grouped by Entity, migration, ERD, repository, service/test, DTO/mapper, controller/test, and roadmap.
- Build result.
- Only decisions made when both the spec and the conventions were silent — skip anything that was simply applied per convention.
- Remaining blockers, if any.
- Confirm whether `docs/roadmap.md` was updated to `완료` with the issue number, or explain why it was not updated.

Then continue the merge cycle in the same session, per `AGENTS.md` workflow step 4 and `docs/git-workflow.md §4`:

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

This also applies when `/impl` is run without a prior `/spec` (e.g. simple docs/meta updates) — the merge cycle still completes in the same session.
