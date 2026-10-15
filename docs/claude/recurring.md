# Recurring transactions

## How templates and transactions are linked

The only link is the nullable column `transactions.templateId`. It is a plain `Int`, not a Room
`@ForeignKey`, so nothing cascades on its own (#31 D1). **Only recorded (paid) occurrences are
rows.** Unpaid occurrences are never stored; they are computed from the template's rule on every
emission. An occurrence counts as recorded when a row with that `(templateId, date)` exists, and a
unique index allows at most one. Rows from the Excel importer have `templateId = NULL`, so they
aren't linked to any template.

## Occurrences

`RecurringTransactionTemplate.occurrencesInRange(start, end)` (`data/Recurrence.kt`) expands a
template into due dates. Each date is computed from the fixed anchor (`startDate`), so a monthly
template starting Jan 31 gives Feb 28/29 and then Mar 31, with no drift. `endDate` caps the series.

`recurrenceRangeError` (same file) validates a finite series in `RecurringForm`: the end date must
not precede the start date, and the range must reach the **second** occurrence. Otherwise the
"series" is a single payment, e.g. yearly with a one-month range. The error is shown under the
end date and blocks saving.

The app is **day-accurate by design**. Per-month totals can differ slightly from the user's old
spreadsheet, which was month-granular. That is expected, not a bug.

## Projection and dedup (`mergeItems` in `ui/ExpenseItems.kt`)

The lists combine real transactions with *projected* occurrences of every template in the range.
A projected occurrence is hidden when a transaction exists with the same `(templateId, date)`.
Projections are `ExpenseItem(isRecurring = true, isPaid = false, transactionId = null)`.

Corollary: if a recorded payment's date drifts away from its occurrence date, the occurrence
reappears next to it as unpaid, which looks like a duplicate. For this reason
`EditTransactionScreen` locks the date (`dateEditable = false`) on template-generated transactions.
Unlocking it for unpaid ones needs a stored occurrence date; see #33.
Changing a template's `startDate` or frequency can likewise orphan existing payments.

## Recording a payment

- **Manual:** `togglePaid(item)` on a projected item inserts a paid `Transaction`, copying
  templateId, date, category, account and counterparty.
- **Auto-pay:** for `autoPayment` templates, `repository.materializeAutoPayments(today)` inserts
  every missing occurrence from `startDate` through today. It runs in one `withTransaction`. It
  reads templates and existing `(templateId, date)` keys fresh inside the transaction, so stale
  Flow emissions can't double-insert or bring back a series that was just deleted. It is triggered
  **explicitly**, never by observing the tables: `MainActivity.onResume` → `refreshAutoPayments()`
  (covers app start and a date change while backgrounded), and right after every template write
  (`saveTemplate`/`updateTemplate`) and after an import. Any new code path that
  creates or edits templates must call it too, or past occurrences only appear on the next resume.

## Editing and deleting (`ui/components/ExpenseItemRow.kt`)

- Tapping a recorded payment that belongs to an existing template shows a dialog: **This payment**
  (opens `EditTransactionScreen`, date locked) or **Whole series** (opens the template editor).
- Tapping a one-time transaction (or an orphan whose template was deleted) opens
  `EditTransactionScreen` directly.
- Tapping a projected (unpaid) occurrence opens `EditRecurringTransactionTemplateScreen`.
- Saving a template edit copies its label changes to past payments, except labels that were
  individually edited on a payment (see [data-layer.md](data-layer.md)).
- Deleting any row that belongs to an existing template deletes the **whole series**
  (`deleteSeries`) after a confirmation dialog. To stop a series instead, set an end date.
