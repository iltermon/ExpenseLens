# Test Plan — branch `refactor/transaction-form-core-fields` vs `master`

Covers the 7 committed changes on this branch plus the four uncommitted feature sets
(unsaved-changes back dialog, counterparty on rows, remaining-occurrences, Settings redesign).

## Setup

- Build/install: `./gradlew :app:installDebug` (JAVA_HOME → JetBrains Runtime).
- **Migration matters:** do one run installing **over an existing (v10) install** (do *not* uninstall first), and one **fresh install**.
- Seed data to have before testing: ≥3 accounts; categories of each type (both / expense-only / income-only); ≥2 counterparties with default category+account set; several one-time transactions; one recurring **with a fixed end date**, one **open-ended**; both income and expense; some paid and some unpaid.
- Run the key flows in **English and Turkish** (Settings → Language).

---

## 1. Database migration v10 → v11 (do this FIRST — highest risk)

1. With a pre-existing install that has real data, install the new build over it. **Expected:** app opens, no crash; all transactions, templates, accounts, categories, counterparties intact.
2. Open Settings → Accounts and → Categories. **Expected:** every existing account/category shows normally (not greyed) — i.e. all default to *active*.
3. Fresh install (uninstall then install). **Expected:** seed works, no migration crash.

## 2. Settings landing = compact menu

1. Open Settings. **Expected:** short page — Language + Currency at top; then rows **Accounts, Categories, Counterparties, Templates** (each with a › chevron); then Import Excel + Clear data. No inline lists.
2. **Import Excel is reachable without scrolling past long lists.** Tap it → file picker opens.
3. Tap each of the four rows → opens its own full screen with a working back arrow. Language/Currency still change correctly; Clear-data dialog still works.

## 3. Accounts management screen

1. **Add** (＋ in top bar) → dialog with name + type + monthly/yearly limits → save → appears in list.
2. **Edit** (⋮ → Edit) → **rename** an account + change type + limits → save. **Expected:** name/type/limits update; existing transactions using it keep their link (account is id-based).
3. **Disable** (⋮ → Disable) → row greys out + "Disabled" badge; menu now offers **Enable**. Re-enable restores it.
4. **Delete → Move** (⋮ → Delete): pick another account as target → confirm. **Expected:** that account's transactions/templates now show the target account; counterparty defaults pointing at it repoint.
5. **Delete → Leave unassigned:** choose "Leave unassigned" → confirm. **Expected:** those transactions now have no account.
6. Busy spinner appears briefly during delete.

## 4. Categories management screen

1. **Add / Edit / Enable-Disable** — same as accounts (Edit now allows **rename** + type + limits, not just limits).
2. **Rename cascade (critical):** rename e.g. "Food" → "Groceries". **Expected:** existing transactions, any recurring template, and any counterparty whose *default category* was "Food" all now read "Groceries".
3. **Delete → target is mandatory:** open Delete on a category. **Expected:** there is **no** "leave unassigned" option; the Delete button stays disabled until you pick a replacement category. Confirm → its transactions/templates/counterparty-defaults move to the chosen category; original removed.
4. Disable an income-only vs expense-only vs both category (used in section 7).

## 5. Counterparties management screen

1. **Add / Edit** (rename + default category/account) works.
2. **Merge** (⋮ → Merge) into another → source's transactions repoint, source removed.
3. **Delete → Move** vs **Leave unassigned** (counterparty is nullable, so both offered) behave correctly.
4. No Enable/Disable option here (by design).

## 6. Templates management screen

1. Open Templates. **Expected:** lists all recurring templates; each shows description, frequency (↻ …), amount, and an **Active** or **Ended** chip.
2. **Segmented filter** All / Active / Ended partitions the list correctly (a template whose end date is in the past = Ended; open-ended or future = Active; end date == today counts as Active).
3. **Tap a row** → opens the existing Edit Template screen; edits save.
4. **Delete** (trash) → confirm dialog → removes the template **and its generated transactions** (busy spinner shows). Cancel leaves it.

## 7. Enable/Disable filtering behavior

1. Disable an account **and** a category. Go to **Add Expense** and **Add Income**. **Expected:** the disabled account and disabled category **do not appear** in those pickers.
2. Open an **existing** transaction that already uses the disabled account/category (Edit). **Expected:** it still shows the disabled account/category (not lost).
3. **Analytics** still counts existing transactions on the disabled account/category.
4. Re-enable → they reappear in the Add pickers.

## 8. Unsaved-changes back dialog (Add & Edit forms)

1. Add Expense, change nothing, press back (arrow) and again with **system/gesture back**. **Expected:** leaves immediately, no dialog.
2. Type a description → back → **dialog** (Save / Discard / Cancel). **Discard** → leaves, nothing saved. Redo → **Cancel** → stays, text intact.
3. Fill all fields validly → back → **Save** → saves and leaves (counterparty-defaults prompt still fires if applicable).
4. Fill amount only (no category) → back → **Save** → dialog closes, stays on form with **validation errors shown**, nothing saved.
5. One-time tab: type a description, switch to **Recurring** tab, back → dialog still appears (shared fields carry across tabs).
6. Recurring tab: change only interval/frequency → back → dialog appears.
7. **Edit a transaction that has a counterparty**, back with no edits → **no dialog** (baseline correct). Change amount → back → Save updates in place.
8. Edit Template: change recurrence → Cancel keeps changes, Discard reverts.
9. Repeat 2 & 4 in **Turkish** — dialog title/message/buttons localized ("Yoksay" = Discard).

## 9. Counterparty name on transaction rows

1. Expenses tab: rows with a counterparty show `Category · Counterparty` on the second line; rows without show just the category.
2. Income tab: same.
3. Rename a counterparty (via merge/edit) → rows reflect the new name.
4. A very long counterparty name **ellipsizes** (one line), doesn't push the amount/date.

## 10. Remaining-occurrences on recurring cards

1. A recurring transaction **with a fixed end date** shows `↻ <frequency> · N left`; count decreases toward the end; the final one reads `1 left`.
2. An **open-ended** recurring transaction shows only `↻ <frequency>` (no count).
3. Turkish: reads `↻ Aylık · 6 kaldı`.

## 11. Regression — committed refactors on this branch

- **Shared core fields / field order** (TransactionCoreFields, counterparty moved below amount): Add Expense/Income and Edit forms show fields in order **description → amount → counterparty → category → account**; picking a counterparty prefills its default category/account; validation errors show per-field; save works for one-time and recurring.
- **Filter bar redesign** (TabScreenShell): on Expenses/Income, open the date **range picker**; with a custom range active, the month steppers are **hidden** and a full-width banner shows the range with a clear (✕) that restores the month view. Range headline is vertically centered.
- **Net summary fix** (Analytics): the summary shows **Income, Expense, Net** in that order; Net is positive when income > expenses, negative otherwise.

## 12. General smoke / regression

1. Bottom nav (Expenses / Analytics / Income / Settings) works; sub-screens hide the bottom bar; back returns correctly.
2. Add a one-time expense and income; mark paid/unpaid (✓ / −); swipe-to-delete; edit via tap.
3. Add a recurring template with auto-payment; confirm a projected occurrence appears and marking it paid converts it to a real transaction (no duplicate).
4. Analytics category/account breakdowns and totals still add up.
5. Clear data (Advanced) still clears transactions but keeps accounts/categories/settings.
6. Rotate device / dark mode on a couple of screens — no crash, labels readable.

## 13. Localization

- Switch to Turkish and spot-check every new surface: Settings menu, the four management screens, delete-with-reassign dialog, disabled badge, templates filter/chips, unsaved-changes dialog. No raw keys or missing strings.

---

**Highest-risk items to prioritize:** #1 (migration over existing data), #4.2 (category rename cascade), #4.3 (category mandatory reassign), and #7 (disable filtering). These touch the database or cross-table references where a bug would be data-affecting rather than cosmetic.
