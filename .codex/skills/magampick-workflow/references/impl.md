# /impl Workflow

Implement code from a completed spec. Follow the spec mechanically. Ask the user only when the spec is missing a decision or a complex build/test failure requires interpretation.

## Input

- `{이슈번호}`: required GitHub Issue number.
- Find exactly one matching file: `docs/specs/{N}-*.md`.

## 1. Working Directory Guard

`/impl` must run inside issue #{N}'s git worktree directory (`../magampick-api-{N}-{slug}`, created by `/issue`).

- If on `feat/{N}-*` (the issue type prefix) and `docs/specs/{N}-*.md` is present: continue.
- If on `develop` or `main` (the main directory): stop. Report the worktree path (`git worktree list`) and ask the user to launch the agent there and re-run `/impl {N}`.
- If no worktree exists: tell the user to run `/issue` or `/spec {N}` first, then stop.

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
12. Update `docs/roadmap.md`

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
- DTO request/response shapes follow `docs/api-convention.md`.
- Controllers and DTOs must include Springdoc OpenAPI annotations from `docs/api-convention.md`.
  - Controller class: `@Tag`.
  - Controller method: `@Operation` and success / major error `@ApiResponse`.
  - DTO record and components: `@Schema`.
  - Path / query parameters: `@Parameter` when useful.
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

## 8. Completion Report

Report:

- Created/modified files grouped by Entity, migration, ERD, repository, service/test, DTO/mapper, controller/test, and roadmap.
- Build result.
- Any small spec-adjacent implementation choices made.
- Remaining blockers, if any.
- Confirm whether `docs/roadmap.md` was updated to `완료` with the issue number, or explain why it was not updated.

Do not commit, push, or create a PR unless the user explicitly requests it and approves the exact commit message and file list first.
