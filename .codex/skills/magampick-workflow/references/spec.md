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

`/spec` must run inside issue #{N}'s git worktree directory. Normally `/issue` already created it; this step verifies the location and provides a fallback.

Check the current location with `git branch --show-current` and `git worktree list`:

- On `feat/{N}-*` (the issue type prefix): inside the worktree. Continue.
- On `develop` or `main` (the main directory):
  - If a `feat/{N}-*` worktree already exists: tell the user its path, ask them to launch the agent there and re-run `/spec {N}`, then stop.
  - If no worktree exists (fallback, e.g. an issue created directly on GitHub): bootstrap it, then stop with the same instruction.

Fallback bootstrap (only when the worktree is missing):

1. Build a slug from the issue title using `docs/glossary.md` English mappings (remove the emoji/type prefix, kebab-case the English words). Confirm undecided terms with the user.
2. Create the branch and worktree:
   ```powershell
   gh issue develop {N} --repo MagamPick/magampick-api --base develop --name "feat/{N}-{slug}"
   git worktree add ../magampick-api-{N}-{slug} "feat/{N}-{slug}"
   ```
3. Tell the user the worktree path and to re-run `/spec {N}` from there, then stop.

`/spec` (spec save) and `/impl` all run inside this worktree. Never work in the main directory on `develop` or `main`.

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
```

Follow `docs/api-convention.md`. Specify the payload only; the `ApiResponse<T>` envelope is applied globally.

## 6. Data Model Rules

- IDs are `BIGINT`.
- Enums use `VARCHAR + CHECK`.
- Location columns use `GEOGRAPHY(POINT, 4326)` when applicable.
- Migration filename uses timestamp format: `V{YYYYMMDDHHMMSS}__{description}.sql`.
- Existing merged migrations must not be edited.

## 7. Save After Final Approval

Save only after final user approval:

```text
docs/specs/{N}-{slug}.md
```

If the file already exists, ask before overwriting.

## Result Report

Report the saved file path and suggest `/impl {N}` as the next workflow.
