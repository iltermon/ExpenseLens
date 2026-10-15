# Workflow

- Work items are tracked as **GitHub issues** (`gh issue list`). There are no task files in the
  repo: new task notes become issues.
- Default branch is `master`; work on feature branches (`fix/...`, `feature/...`, `refactor/...`).
- Commit only when asked.

## Roadmap (as of 2026-10-14)

1. **Correctness bugs:** #21 recurring edits/duplicates, #18 Sankey totals, #20 edits not showing
   and long names, #15 recurring date validation, #14 trim text inputs.
2. **UX polish:** #22 rename "Templates", #13 templates alignment, #16 TR summary labels, #19
   per-type default categories, #17 date display format.
3. **Features:** #11 XLSX export, #12 cloud backup, #25 shared 3-dots menu, #26 Analytics date range,
   #27 notifications, #33 editable date for unpaid recurring payments, #32 generic node/link Sankey (`:sankey` takes explicit nodes with columns +
   links; all budget logic moves to `SankeyMapper`).
4. **Review follow-ups** (2026-07-03 code review): #28 correctness hardening, #29 conventions,
   #30 structural refactors, #31 nice-to-haves.

Phase 1 (#14, #15, #18, #20, #21) is merged (PR #34). #18 uses "all income is income, expenses
gross" so the graph matches the summary card; a parallel Codex session's alternative definition is
parked in #32. Don't act on it until the user picks it up.

**Current focus: #30 structural refactor**, re-scoped into three PRs (see the issue): PR 1 safety-net
tests + explicit auto-pay trigger (branch `refactor/review-c-pr1`), PR 2 ViewModel split, PR 3
duplicate screens. Keep each PR reviewable; don't widen scope.
