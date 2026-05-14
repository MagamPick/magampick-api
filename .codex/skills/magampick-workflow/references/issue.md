# /issue Workflow

Create a GitHub Issue for a new feature. This is the policy and scope decision stage; do not decide missing product behavior yourself.

## Input

- `{기능명}`: Korean free-form feature name.
- If missing, ask the user for the feature name.

## 1. Read-Only Context Check

Read only the relevant context before drafting:

- `docs/features.md`: confirm the feature is in scope.
- `docs/product.md`: confirm it does not conflict with Out of Scope or Pending Decisions.
- `docs/glossary.md`: collect domain terms and English mappings.
- `docs/policy.md`: check policy impact.
- `docs/erd/overview.md`: infer the domain label.
- `.github/ISSUE_TEMPLATE/feature.md`: use the repository issue template.

If the feature is out of scope or depends on a pending decision, stop and discuss the decision with the user.

## 2. Draft The Five Sections

Fill one section at a time, show it to the user, and continue only after approval:

1. Context: why the feature exists and business background.
2. Scope: In Scope and Out of Scope for this issue.
3. User Roles: Customer / Seller / Admin when relevant.
4. Core Policy Decisions: decisions from `policy.md` / `product.md` needed for this feature.
5. Business Logic: high-level flow only; detailed design belongs in `/spec`.

## 3. Docs Updates

At `/issue` stage, docs updates are allowed only for:

- `docs/product.md`
- `docs/features.md`
- `docs/policy.md`
- `docs/glossary.md`

Global conventions such as API, coding, test, commit, and git workflow docs require a separate issue.

## 4. Labels And Title

- Type label: usually `feat`; confirm with the user if uncertain.
- Domain label: choose one from `users`, `stores`, `products`, `orders`, `payments`, `reviews`, `notifications`, `benefits`, `operations`, `statistics`.
- Title format follows `docs/commit-convention.md`, e.g. `✨ feat: 매장 등록 신청`.
- Assume labels already exist. If a required label is missing, report and stop.

## 5. Final Approval Before Creating Issue

Show the full title, body, and labels. Do not run `gh issue create` until the user approves.

Use a PowerShell here-string for Korean multiline body:

```powershell
gh issue create `
  --repo MagamPick/magampick-api `
  --title "✨ feat: {기능명}" `
  --body @'
{body}
'@ `
  --label "feat,{domain}"
```

## 6. Result Report

Report the issue number and URL. Suggest `/spec {N}` as the next workflow.

## Error Handling

- `gh --version` fails: tell the user GitHub CLI is missing.
- `gh auth status` fails: ask the user to authenticate with `gh auth login`.
- `gh repo view` fails: report repository detection/auth issue.
- Template or required docs missing: report the missing file and stop.
