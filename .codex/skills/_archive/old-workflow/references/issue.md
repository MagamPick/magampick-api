# /issue Workflow

Create a GitHub Issue. Pick the type (`feat` / `fix` / `refactor` / `docs` / `chore`) first; the type drives the template, label, title emoji, and downstream workflow. This is the policy and scope decision stage; do not decide missing product behavior yourself.

## Input

- `{기능명}`: Korean free-form feature name.
- If missing, ask the user for the feature name.

## 1. Type Decision + Read-Only Context Check

**Type decision first** — it drives the template, label, emoji, and downstream workflow:

| Type | Template | Downstream workflow |
|---|---|---|
| `feat` | `.github/ISSUE_TEMPLATE/feature.md` | `/issue` → `/impl` (plan mode → code → merge) |
| `fix` | `.github/ISSUE_TEMPLATE/fix.md` | `/issue` → `/impl` (plan mode → code → merge) |
| `refactor` | `.github/ISSUE_TEMPLATE/refactor.md` | `/issue` → `/impl` (applicable steps only) |
| `docs` / `chore` | `.github/ISSUE_TEMPLATE/docs.md` | `/issue` → `/impl` (edit files → build sanity → merge) |

> `/spec` is opt-in for handoff scenarios (delegation to another session/model, external contractor, multi-stakeholder review). Not part of the default flow — invoke explicitly only when needed.

How to choose:
- New / changed code behavior → `feat`
- Bug fix → `fix`
- Code structure / readability / performance, same behavior → `refactor`
- Documentation / convention / workflow / build / infrastructure → `docs` (also use this template for `chore`)

Confirm the type with the user (default = `feat`).

**Read-only context check** — by type:

- `feat` / `fix`: `docs/features.md`, `docs/product.md`, `docs/glossary.md`, `docs/policy.md`, `docs/erd/overview.md`.
- `refactor` / `docs` / `chore`: the target files, related SKILLs, related convention docs.

If a `feat` / `fix` is out of scope or depends on a pending decision, stop and discuss with the user.

## 2. Draft The Body (Conversational)

Follow the template for the chosen type. Fill one section at a time, show it to the user, and continue only after approval.

### Type = feat → `.github/ISSUE_TEMPLATE/feature.md` (4 sections)

1. Context: why the feature exists and business background.
2. Scope: In Scope and Out of Scope.
3. Core Policy Decisions: decisions from `policy.md` / `product.md` plus impactful decisions that `/impl`'s plan mode will use as the agreement baseline. Check the following list for omissions:
   - Cardinality (1:1 vs 1:N, single vs multi-select for collection fields)
   - Role-based branching (consumer / seller / admin differences)
   - Index / unique impact (new search patterns, unique constraints like email/nickname)
   - Migration impact (adding NOT NULL columns to existing tables, FK changes)
   - Enum candidates / state values (missing candidates cause downstream branching loss)
   - External system dependency (external API / email / notification — including Mock decision)

   If a decision conflicts with `features.md` / `policy.md` or is ambiguous, do not assume — surface options and confirm.
4. Business Logic: high-level flow only; mark actors when multiple roles are involved (e.g. `seller publishes → customer redeems`). Detailed design happens in `/impl`'s plan mode.

### Type = fix → `.github/ISSUE_TEMPLATE/fix.md`

1. Symptom: what's wrong.
2. Reproduction: steps / inputs.
3. Expected behavior.
4. References: screenshots / logs / environment (when relevant).

### Type = refactor → `.github/ISSUE_TEMPLATE/refactor.md`

1. Current pain point.
2. Change direction.
3. Expected benefit.

### Type = docs / chore → `.github/ISSUE_TEMPLATE/docs.md` (4 sections)

1. Context: why the change, background, trigger.
2. Changes: which files / sections, high-level (precise diff lives in the PR).
3. Out of Scope: what is intentionally not touched.
4. Impact / Follow-up (when relevant): other docs to sync, one-time setup, post-merge work, other slots to update.

## 3. Docs Updates

At `/issue` stage, docs updates are allowed only for:

- `docs/product.md`
- `docs/features.md`
- `docs/policy.md`
- `docs/glossary.md`

Global conventions such as API, coding, test, commit, and git workflow docs require a separate issue.

## 4. Labels And Title

- Type label: the type chosen in §1 (`feat` / `fix` / `refactor` / `docs` / `chore`).
- Domain label: only `feat` / `fix` — choose one from `users`, `stores`, `products`, `orders`, `payments`, `reviews`, `notifications`, `benefits`, `operations`, `statistics`. `refactor` / `docs` / `chore` skip domain labels (workflow / meta changes are domain-agnostic).
- Title format follows `docs/commit-convention.md`, type emoji by type:
  - `✨ feat:` / `🐛 fix:` / `♻️ refactor:` / `📝 docs:` / `🔧 chore:`
- Assume labels already exist. If a required label is missing, report and stop.

## 5. Final Approval Before Creating Issue

Show the full title, body, and labels. Do not run `gh issue create` until the user approves.

Use a PowerShell here-string for Korean multiline body:

```powershell
gh issue create `
  --repo MagamPick/magampick-api `
  --title "<emoji> <type>: {기능명}" `
  --body @'
{body}
'@ `
  --label "<type>[,<domain>]"
```

## 6. Create Working Branch And Attach To A Slot

After the issue is created, create the branch on origin and attach it to an empty slot. Run this from the main repo directory.

Build a slug from the issue title using `docs/glossary.md` English mappings: remove the emoji/type prefix, kebab-case the English words (e.g. `매장 등록 신청` -> `store-registration`). Confirm undecided glossary terms with the user.

Find an empty slot:

```powershell
git worktree list
```

A slot showing `(detached HEAD)` is empty. The default slot pool is `magampick-api-wt1/wt2/wt3` (see `AGENTS.md` §"병렬 운영"). If all slots are occupied, ask the user to clean up a slot or add a temporary slot, then stop.

Create the branch and attach to the chosen empty slot:

```powershell
gh issue develop {N} --repo MagamPick/magampick-api --base develop --name "feat/{N}-{slug}"
git -C ../magampick-api-wtX switch "feat/{N}-{slug}"
```

- `gh issue develop` creates the issue-linked branch on origin (PR merge auto-closes the issue). Do not pass `--checkout`; the main directory stays on `develop`.
- `git -C ../magampick-api-wtX switch` attaches the branch to the chosen empty slot (replace `wtX` with the actual slot number).
- Adjust the branch prefix if the type is not `feat` (`fix/`, `refactor/`, `docs/`, ...).

## 7. Result Report

Report the issue number, URL, and the slot path the branch was attached to. Tell the user to launch the agent from inside the slot for the next step:

```
cd ../magampick-api-wtX
codex   # or claude
/impl {N}
```

If handoff is needed (delegate to another session / model / external contractor / pre-implementation review), the user may invoke `/spec {N}` first before `/impl`.

## Error Handling

- `gh --version` fails: tell the user GitHub CLI is missing.
- `gh auth status` fails: ask the user to authenticate with `gh auth login`.
- `gh repo view` fails: report repository detection/auth issue.
- Template or required docs missing: report the missing file and stop.
