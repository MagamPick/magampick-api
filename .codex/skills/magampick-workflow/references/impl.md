# /impl Workflow

Implement code from a completed spec. Follow the spec mechanically. Ask the user only when the spec is missing a decision or a complex build/test failure requires interpretation.

## Input

- `{이슈번호}`: required GitHub Issue number.
- Find exactly one matching file: `docs/specs/{N}-*.md`.

## 1. Branch Guard

Before editing, verify the current branch:

- If on `main` or `develop`, stop.
- If not on the matching issue branch, ask before creating/checking out a branch.
- Expected branch shape: `{type}/{N}-{slug}`, usually `feat/{N}-{slug}`.

## 2. Load Spec

Read all spec sections:

- Context / Scope / User Roles
- API Specification
- Data Model
- Business Logic
- External Dependencies
- Implementation Notes

If no spec exists, tell the user to run `/spec {N}` first. If multiple specs match, ask the user to choose.

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
5. Service unit tests
6. Service implementation
7. DTOs and MapStruct mapper
8. Controller `@WebMvcTest`
9. Controller implementation
10. `./gradlew spotlessApply`
11. `./gradlew build`

## 5. Coding Rules

- Entity path: `src/main/java/{package}/{domain}/entity/{Name}.java`.
- Repository extends `JpaRepository<Entity, Long>`.
- Basic CRUD repositories do not need tests.
- Custom queries need focused `@DataJpaTest`.
- Service tests use Mockito + AssertJ and Korean method names.
- Controller tests use MockMvc + Mockito.
- Use `@WithMockUser` or security context setup when auth is required.
- DTO request/response shapes follow `docs/api-convention.md`.
- Exceptions follow `BusinessException` + `BaseErrorCode` patterns from `docs/coding-convention.md`.

## 6. Docs Allowed During /impl

Allowed:

- `docs/erd/tables/{table}.md`
- `docs/auth.md` when an authentication/authorization policy decision is part of the spec

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

## 8. Completion Report

Report:

- Created/modified files grouped by Entity, migration, ERD, repository, service/test, DTO/mapper, controller/test.
- Build result.
- Any small spec-adjacent implementation choices made.
- Remaining blockers, if any.

Do not commit, push, or create a PR unless the user explicitly requests it and approves the exact commit message and file list first.
