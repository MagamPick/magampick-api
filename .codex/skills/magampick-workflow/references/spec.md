# /spec Workflow

Create an implementation spec from an approved GitHub Issue. The spec is the single file `/impl` should be able to implement from.

## Input

- `{이슈번호}`: required GitHub Issue number.
- Spec filename: `docs/specs/{N}-{slug}.md`.

## 1. Load Issue

Use GitHub CLI:

```powershell
gh issue view {N} --repo MagamPick/magampick-api --json number,title,body,labels
```

Extract:

- Feature name from title after removing the emoji/type prefix.
- Five issue sections from the body.
- Domain label from labels.

If the issue is missing required sections or still has undecided policy/scope, stop and send the user back to `/issue`.

## 2. Working Directory Guard

`/spec` must run inside the slot where issue #{N}'s branch is attached. Normally `/issue` already attached it; this step verifies the location and provides a fallback.

Check the current location with `git branch --show-current` and `git worktree list`:

- On `feat/{N}-*` (the issue type prefix): inside the slot. Continue.
- On `develop` or `main` (the main directory):
  - If issue #{N}'s branch is attached to some slot (`git worktree list` shows `feat/{N}-*`): tell the user that slot's path, ask them to launch the agent there and re-run `/spec {N}`, then stop.
  - If the branch is not attached anywhere (fallback, e.g. an issue created directly on GitHub): bootstrap it, then stop with the same instruction.

Fallback bootstrap (only when the branch is not attached anywhere):

1. Build a slug from the issue title using `docs/glossary.md` English mappings (remove the emoji/type prefix, kebab-case the English words). Confirm undecided terms with the user.
2. Find an empty slot via `git worktree list` (a slot showing `(detached HEAD)`). The default pool is `magampick-api-wt1/wt2/wt3` (see `AGENTS.md` §"병렬 운영"). If all slots are occupied, ask the user to clean up a slot or add a temporary slot, then stop.
3. Create the branch and attach:
   ```powershell
   gh issue develop {N} --repo MagamPick/magampick-api --base develop --name "feat/{N}-{slug}"
   git -C ../magampick-api-wtX switch "feat/{N}-{slug}"
   ```
4. Tell the user the slot path and to re-run `/spec {N}` from there, then stop.

`/spec` (spec save) and `/impl` all run inside this slot. Never work in the main directory on `develop` or `main`.

## 3. Read-Only Context Check

Read the relevant docs before drafting:

- `docs/features.md`
- `docs/policy.md`
- `docs/glossary.md`
- `docs/erd/overview.md`
- `docs/coding-convention.md`
- `docs/api-convention.md`
- `docs/auth.md`
- `docs/test-convention.md`

## 4. Draft The Spec With User Review

Draft one section at a time and get user approval for each section. After all sections are complete, show the full spec and get final approval before saving.

Required sections:

1. Context: copy issue context, with an issue link line at the top.
2. Scope: copy issue scope.
3. User Roles: copy from issue when relevant.
4. API Specification: endpoints, auth, params, request/response payloads, errors.
5. Data Model: new tables, existing table changes, migration plan, ERD docs.
6. Business Logic: processing flow, validation, state transitions, errors, edge cases, side effects, test cases.
7. External Dependencies: external APIs, environment variables, failure handling when relevant.
8. Implementation Notes: transaction boundaries, relationships, async, adapter, cache, concurrency, exceptions when relevant.

## 5. API Specification Format

For each endpoint:

```markdown
### {METHOD} {PATH}

**Description**:
**Authentication**:

**Path Parameters**
| 파라미터 | 타입 | 설명 |

**Query Parameters**
| 파라미터 | 타입 | 필수 | 설명 |

**Request Body** ({DtoName})
| 필드 | 타입 | 제약 | 설명 |

**Response** - {StatusCode} ({DtoName})
| 필드 | 타입 | 설명 |

**Error Responses**
| 상태 | 에러 코드 | 상황 |

**OpenAPI / Swagger**
- Controller `@Tag` name / description
- Method `@Operation` summary / description
- Success and major error `@ApiResponse`
- DTO / field `@Schema` descriptions and examples
- Path / query `@Parameter` descriptions and examples when useful
```

Follow `docs/api-convention.md`. Specify the payload only; the `ApiResponse<T>` envelope is applied globally.
Include enough descriptions, examples, and constraints in the spec so `/impl` can add Springdoc OpenAPI annotations without guessing.

## 6. Length And Range Constraints

- Request/Response DTO fields should specify string length, numeric range, collection size, and format constraints when applicable.
- Data Model should specify `VARCHAR` length, numeric precision/scale, nullable, unique, and check constraints when applicable.
- If a length/range constraint is not already decided in the issue or docs, do not invent it silently. Ask the user to confirm.
- When asking, include a recommended value and a short reason.
  - Example: `nickname`: recommend 2-20 characters because it is short enough for UI display and long enough for Korean/English nicknames.
  - Example: `email`: recommend `VARCHAR(255)` because it follows common email storage limits and works well with unique indexes.

## 7. Data Model Rules

- IDs are `BIGINT`.
- Enums use `VARCHAR + CHECK`.
- Location columns use `GEOGRAPHY(POINT, 4326)` when applicable.
- Migration filename uses timestamp format: `V{YYYYMMDDHHMMSS}__{description}.sql`.
- Existing merged migrations must not be edited.

## 8. Save After Final Approval

Save only after final user approval:

```text
docs/specs/{N}-{slug}.md
```

If the file already exists, ask before overwriting.

## Result Report

Report the saved file path and suggest `/impl {N}` as the next workflow.
