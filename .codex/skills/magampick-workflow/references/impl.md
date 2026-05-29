# /impl Workflow (Notion-driven)

Implement a feature whose specification lives in the MagamPick Notion "기능 명세 (Features)" database. The Notion page carries policy decisions, scope, and domain-specific behavior. The plan-mode agreement carries in-session decisions made on top of the Notion page. The convention docs carry mechanical detail (Swagger annotations, package layout, transaction placement, logging, test enumeration, migration format, etc.). Follow the Notion page + plan agreement where they speak, fall back to the conventions when they are silent on mechanical detail, and ask the user only when all three are silent — and write any new decision back into the Notion page body so it survives the session.

GitHub Issues are not used in this workflow.

## Input

- `<notion-url>`: a page URL from the Notion "기능 명세 (Features)" database. Required.

## Two Modes

`/impl` runs in one of two modes based on where it is called:

| Mode | Called from | Steps |
|---|---|---|
| **A. Bootstrap** | Main directory (`develop` / `main`) | §1 Notion fetch → §2 plan agreement → §3 Notion status `기획` → `개발중` → §4 slot attach → guidance + stop |
| **B. Execute** | Slot (`feat/<slug>` etc.) | §1 Notion fetch (re-load) → §5 TDD implementation → §6 build + external review → §7 merge cycle → §8 Notion status update |

Detect with `git branch --show-current` / `git worktree list`.

In Codex, the canonical procedure is the two-step call: run mode A from the main directory, then have the user launch a new Codex session inside the slot directory and call `/impl <notion-url>` again to run mode B. (Claude Code can collapse this into one call via `EnterWorktree`, which Codex does not have.)

## 1. Fetch The Notion Page

Use the Notion MCP fetch tool:

```text
notion-fetch(id=<notion-url>)
```

Read these properties:
- `기능명` (title), `분류`, `사용자`, `상태`, `설명`
- Page body (markdown)
- Relations: `관련 정책`, `관련 결정` (each is a JSON array of page URLs when populated)

Expand relations only when needed:
- If the body + `설명` already pin down the policy / scope / domain decisions, skip the relation fetches.
- If not, fetch each `관련 정책` / `관련 결정` URL with another `notion-fetch` call.
- Fetch `관련 외부연동` / `관련 Phase` only when the body explicitly references them.

Decide the type from the page intent:
- New / changed behavior → `feat`
- Bug fix → `fix`
- Code structure / readability / performance with the same behavior → `refactor`

(`docs` / `chore` are unrelated to the Notion feature DB. Those come through direct user instructions without a Notion URL and are out of scope for this skill.)

## 2. Plan Mode Entry + Agreement (mode A only)

Before any code or file edit, enter plan mode and reach explicit user agreement. In Codex this means emitting a structured plan in chat and waiting for an explicit user approval before proceeding.

Plan content:

1. **Notion page summary** — feature name, classification, user roles, current status, body highlights.
2. **Sufficiency check** — are the policy / scope / API intent / domain decisions present in the Notion body (and relations when expanded)?
   - If gaps or ambiguity exist, surface options to the user, get a decision, and **write the decision back into the Notion page body** with `notion-update-page` (`update_content` or `insert_content`). The decision must persist so any future session / model reading the same page reaches the same interpretation.
3. **Impactful decisions** (only surface what is missing from the page):
   - Cardinality (1:1 vs 1:N, single vs multi-select)
   - Role-based branching (seller / customer / admin)
   - Index / unique impact
   - Migration impact (NOT NULL additions, etc.)
   - Enum candidates / state values
   - External system dependency (Mock vs real integration)
4. **Splitting agreement** — single PR or multiple PR steps?
   - Single PR: proceed as is.
   - Multiple PRs: write a step checklist into the Notion page body (e.g. `- [ ] Step 1: Entity + migration`, `- [ ] Step 2: Service + Controller`). This `/impl` call advances only the first step.
5. **Applicable step range** — which steps from §5 apply for this run.

Agreement rules:
- Only raise decisions that the Notion page + conventions both leave silent. Do not re-litigate what the page already pins.
- Plan agreement = "proceed this way." After exit, run §3 → §4 (still in mode A).

### Type Branching

| Step | feat / fix | refactor |
|---|---|---|
| §5 TDD implementation (Entity ~ Controller + tests) | full | applicable layer only |
| §5-6 integration test (golden path) | signup / order / payment / refund only | when applicable |
| §5-7 `./gradlew spotlessApply` | ✓ | ✓ |
| §5-8 `./gradlew build` | ✓ | ✓ |
| §6 external model review | ✓ | ✓ |
| §7 roadmap update | ✓ | when applicable |
| §8 merge cycle + Notion status update | ✓ | ✓ |

## 3. Flip Notion Status `기획` → `개발중` (mode A)

```text
notion-update-page(
  page_id=<page-id>,
  command="update_properties",
  properties={ "상태": "개발중" }
)
```

For a multi-PR split, do this once at the start of the first step. Subsequent PRs only update the body checklist.

## 4. Attach The Slot (mode A)

Build a slug from the Notion `기능명` using `docs/glossary.md` English mappings (kebab-case the English words; e.g. `매장 등록 신청` → `store-registration`). Confirm undecided glossary terms with the user.

For a multi-PR split, suffix the slug with `-step{N}` (e.g. `store-registration-step1`).

Find an empty slot:

```powershell
git worktree list
```

A slot showing `(detached HEAD)` is empty. The default pool is `magampick-api-wt1/wt2/wt3` (see `AGENTS.md` §"병렬 운영"). If all are occupied, ask the user to clean up a slot or add a temporary slot, then stop.

Create the branch on the chosen empty slot (no `gh issue develop` — there is no issue):

```powershell
git -C ../magampick-api-wtX switch -c feat/<slug> origin/develop
```

Adjust the prefix when the type is not feat (`fix/`, `refactor/`). The remote push happens after the first commit in §8 (`git push -u origin feat/<slug>`).

Report and stop:

> Slot attached: `../magampick-api-wtX` (branch: `feat/<slug>`)
> Notion status: 기획 → 개발중
> Next: launch the agent from that directory and re-run `/impl <notion-url>` (mode B).

## 5. TDD Red → Green Implementation (mode B)

Principle: write the test → run it and confirm it fails (red) → write the implementation → re-run and confirm it passes (green). Cycle per layer (Service, Controller).

### 5-1. Entity / Migration / ERD (no TDD — schema)

- Entity: `src/main/java/{package}/{domain}/entity/{Name}.java` (`docs/coding-convention.md`).
- Migration: `src/main/resources/db/migration/V{YYYYMMDDHHMMSS}__{description}.sql`. Timestamp format. Never edit already-merged migrations.
- ERD: `docs/erd/tables/{table}.md`.

### 5-2. Repository

- `extends JpaRepository<Entity, Long>`.
- Basic CRUD: no test.
- Custom query: write `@DataJpaTest` first → run → red → implement the query → green.

### 5-3. Service (TDD red→green)

1. Write the test first: `src/test/java/{package}/{domain}/service/{Name}ServiceTest.java`. Korean method names with `// given-when-then`. Mockito + AssertJ.
2. Run `./gradlew test --tests "*{Name}ServiceTest"` and confirm red (compile error or failure).
3. Implement the service: `src/main/java/{package}/{domain}/service/{Name}Service.java`. Use the Notion page + plan agreement for behavior; `BusinessException` + `BaseErrorCode` for failures (`docs/coding-convention.md`).
4. Re-run the same `./gradlew test` invocation and confirm green.

### 5-4. DTO + MapStruct Mapper (no TDD — signatures)

Follow `docs/api-convention.md`. Bean Validation annotations. MapStruct per `docs/coding-convention.md` §9. No self-tests for DTOs / mappers.

### 5-5. Controller (TDD red→green)

1. Write the test first: `src/test/java/{package}/{domain}/controller/{Name}ControllerTest.java`. MockMvc + Mockito. `@WithMockUser` when auth is required (`docs/auth.md`).
2. Run and confirm red.
3. Implement the controller: `src/main/java/{package}/{domain}/controller/{Name}Controller.java`. URL / verb / status from Notion + plan + `docs/api-convention.md`. Response wrapping via `ApiResponse<T>` (global advice — return the payload only). Springdoc annotations per `docs/api-convention.md` §12. Authorization via `@PreAuthorize` or `SecurityConfig` per `docs/auth.md`.
4. Re-run and confirm green.

### 5-6. Integration Test (golden path only)

When the feature is on the golden path (signup / order / payment / refund), write an integration test before the controller and service implementations are finalized — let it stay red until the layers above are wired up, then turn green.

- Location: `src/test/java/{package}/{domain}/{Name}IntegrationTest.java`
- `@SpringBootTest @AutoConfigureMockMvc @Transactional` + `extends PostgresTestBase` (`docs/test-convention.md` §8 / §10)
- Real DB (Testcontainers PostGIS), real Service↔Repository wiring
- Purpose: break the AI self-reference risk that mock-heavy unit/slice tests cannot catch (transaction boundary, FK, security filter, validation flow).

Skip integration tests for non-golden-path features.

### 5-7. spotlessApply

```powershell
./gradlew spotlessApply
```

### 5-8. build

```powershell
./gradlew build
```

On simple failures (typo / import / format) self-correct and rerun once or twice. On complex failures (logic / interpretation of the Notion body), report to the user and ask for direction.

## 6. External Model Review (feat / fix / refactor)

After the build passes, before committing.

- Default model: **Codex 5.5 medium**. The user may pick a different model at the start of the run (Codex 5.5 high / Opus 4.7 / a different Sonnet instance) based on remaining tokens and importance. Prefer a different family from the implementer.
- Read-only consultation from inside the current slot — no extra worktree.
- Invocation: agent-appropriate (Claude Code: Agent tool or headless `claude -p ...`; Codex: new session).
- Review prompt — cover all 8 categories, critiquing case-fit even when conventions are ticked off:
  1. Intent alignment with the Notion page + plan agreement (no over-engineering, in scope)
  2. Encapsulation / OO — Tell-Don't-Ask, predicate naming (state vs capability), SRP, creation pattern consistency
  3. Spring Boot practices — `@Transactional` boundaries, exception handling, multipart, MapStruct, `@PageableDefault`, validation appropriateness
  4. Security — auth matchers, input validation, privilege bypass, sensitive data exposure
  5. Performance — N+1, fetch strategy, missing pagination
  6. Convention adherence — fit for case, consistency in silent areas, better alternatives
  7. API / response — HTTP status correctness, response envelope, `@ApiResponses` coverage (incl. 401/403), idempotence
  8. Tests — coverage (happy + edge + authz), assertion meaningfulness, integration test necessity
- Reflection: implementer does NOT auto-apply. User reads the review, selects items to address, the implementer applies them. If changes are made, re-run §5-7 and §5-8.

## 7. Roadmap Update

In `docs/roadmap.md`, find the row for the implemented feature:

- Change status `미착수` → `완료`.
- Record the Notion page URL and (after merge) the PR number in the link / note column.
- Add a row if the feature does not exist in the roadmap.
- Skip the update when the build failed or implementation was stopped.

## 8. Completion Report + Merge Cycle + Notion Status Update

After the build passes, report to the user:

- Created/modified files grouped by Entity, migration, ERD, repository, service/test, DTO/mapper, controller/test, and roadmap.
- Build result.
- Only decisions made when the Notion page + plan + conventions were all silent — skip anything that was simply applied per convention or already agreed in §2 plan.
- Remaining blockers, if any.

Then continue the merge cycle in the same session per `AGENTS.md` workflow and `docs/git-workflow.md §4`:

1. **Commit message review** — Subject line only (`<emoji> <type>: <subject>`); no body or footer (`docs/commit-convention.md` §2). Show the exact message and file list; wait for approval. The `commit-msg` hook will reject commits that include a body — never bypass with `--no-verify`.
2. **Commit + push** — first push: `git push -u origin feat/<slug>`.
3. **PR body review** — Show the PR title and body; wait for approval. Include the Notion page URL in the body (and `Step N/M` notation for multi-PR splits). This approval delegates the rest of the merge cycle.
4. **Create the PR** with `gh pr create --base develop ...`.
5. **Watch CI** — Run `gh pr checks {PR#} --repo MagamPick/magampick-api --watch` in the background. No extra polling or sleeping.
6. **Auto-merge on green** — On CI success, immediately run `gh pr merge {PR#} --merge --delete-branch` without an additional prompt (CI is the merge gate per `docs/git-workflow.md §4`). Verify with `gh pr view {PR#} --json state,mergedAt,mergeCommit`.
7. **Slot cleanup + develop pull**:
   ```sh
   git fetch --prune
   git switch --detach origin/develop                # detach current slot
   git branch -D feat/<slug>                          # delete local branch
   git -C "{absolute path to main directory}" pull   # update develop in main
   ```
8. **Notion status update**:
   - **Single-PR completion** → `상태` `개발중` → `운영중`:
     ```text
     notion-update-page(
       page_id=<page-id>,
       command="update_properties",
       properties={ "상태": "운영중" }
     )
     ```
   - **Multi-PR mid-step** → check off the corresponding checklist item in the page body (`update_content` search-and-replace from `- [ ] Step N: ...` to `- [x] Step N: ...`). Leave `상태` as `개발중`.
9. **Cycle complete report** — PR URL, merge commit, Notion status, next-step guidance.

On CI red: report the failure cause and candidate next actions (fix and add a commit, revert, discuss). Do not force-merge or retry merge without user direction.

## Mid-flow Questions — Naturally (no forced review)

Ask the user only in these situations (most should already be resolved in §2 plan):

- Notion body + plan + conventions are all silent on a decision. If mechanical, pull from convention. If policy, ask the user and **write the decision back into the Notion page body**.
- Build / test fails in a way that simple edits cannot resolve.
- The Notion body has two or more plausible interpretations.
- `docs/auth.md` / `docs/erd/tables/` updates need a policy decision.

## Docs Allowed During `/impl`

Allowed:

- `docs/erd/tables/{table}.md` for the implemented domain.
- `docs/auth.md` when an authorization decision is needed (also reflect it in the Notion body).
- `docs/roadmap.md` for the implemented feature row (status, Notion URL, PR number).

Not allowed without a separate chore PR:

- `docs/api-convention.md`
- `docs/coding-convention.md`
- `docs/test-convention.md`
- `docs/commit-convention.md`
- `docs/git-workflow.md`

## Convention Single Source Mapping

When the Notion body + plan are silent, pull mechanical detail from the conventions — do not invent:

| Topic | Source |
|---|---|
| Swagger / OpenAPI annotation placement and content | `docs/api-convention.md` §12 |
| Package / layer / `@Transactional` placement / exceptions / logging / MapStruct | `docs/coding-convention.md` §1-4, §8, §9, §11 |
| Test kinds / depth / fixtures / Korean test method names | `docs/test-convention.md` |
| Authentication / authorization / self-resource access | `docs/auth.md` |
| Migration format / Enum CHECK / Point / KST | `docs/erd/overview.md` |
| Standard processing flow (JWT → repository.findById → 404 → dirty checking → mapper) | Apply as default — no narration |

## Notes

- Notion is the single source for policy / scope / domain decisions. Decisions found during plan mode must be written back into the page body (`notion-update-page`).
- Mode A (main directory) covers Notion fetch + plan + slot attach. Mode B (slot) covers code work and the merge cycle. Claude Code can collapse this into a single call with `EnterWorktree`; Codex requires two calls.
- TDD red→green per layer (Service, Controller). Entity / DTO / Mapper are not TDD targets (schema / signature).
- No GitHub issue is created. Tracking lives on the PR + the Notion body checklist.
- Migration version = timestamp. Never edit already-merged migrations.
- Korean test method names (`docs/test-convention.md`).
- Never bypass approval gates (commit message / PR body / merge). Never use `--no-verify`.
- PowerShell 5.1: Korean method names / comments are UTF-8 (default for Write). Diagnose Gradle encoding issues at build-fail time.
