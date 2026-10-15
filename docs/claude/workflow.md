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

Branch `fix/phase1-correctness` (based on `refactor/folderStructure`) carries the phase-1 work:
#21, #20, #18, #15 and #14 are implemented and manually tested on device. #18 uses "all income is
income, expenses gross" so the graph matches the summary card. A parallel Codex session proposed a
different definition (income-only sources, refunds reduce expenses, two middle hubs); that
direction is parked under #32. Don't act on it until the user picks it up.
