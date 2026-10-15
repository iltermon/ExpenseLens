# Data layer (`data/`)

Room database `ExpenseLensDatabase` (singleton), **version 12**, `exportSchema = false`. Migrations
are hand-written in that file; add a new `MIGRATION_n_m` for every schema change.

## Entities

| Entity | Table | Notes |
|---|---|---|
| `Transaction` | `transactions` | A concrete payment. `date` ISO day, `isPaid`, `isExpense`, optional `categoryId`/`accountId`/`counterpartyId`, and `templateId` when generated from a recurring template. Unique index on `(templateId, date)`; NULL templateIds are never constrained. |
| `RecurringTransactionTemplate` | `recurring_templates` | `startDate`/`endDate` are ISO **days** (`endDate = null` means open-ended). `frequencyInterval` × `frequencyUnit` (`Day`/`Week`/`Month`/`Year`; the legacy entity default `"Monthly"` falls back to Month, see #28 A3). `autoPayment` flag. |
| `Category` | `categories` | `type`: `null` = both, `"expense"`, `"income"`. Optional monthly/yearly limits, `active`. |
| `Account` | `accounts` | `type` is an English display string (see #31 D2). Limits, `active`. |
| `Counterparty` | `counterparties` | Store or payer. Unique `name`, `defaultCategoryId`/`defaultAccountId` prefill forms. |
| `AppSetting` | `app_settings` | Key/value store (e.g. `currency_symbol`). The language is **not** stored here: it lives in SharedPreferences (`LocaleManager`) because it's needed in `attachBaseContext` before the DB exists. |

References are plain `Int?` ids with no Room `@ForeignKey` (#31 D1). `active = false` hides an
account or category from *new*-transaction pickers only. Edit screens and analytics still show it.

## Repository (`ExpenseLensRepository`)

Mostly a thin wrapper over the DAOs. Multi-step writes use `db.withTransaction` so Room fires one
invalidation and the auto-pay collector never sees a half-applied state:

- `deleteSeries(template)`: deletes the template, then every transaction with its `templateId`.
- `updateTemplate(template)`: saves the template **and** carries description/category/account/
  counterparty changes onto its already-generated transactions. A field is only overwritten where it
  still equals the template's *old* value, so labels edited on a single payment survive. Amount and
  date are never touched.
- `materializeAutoPayments(today)`: see [recurring.md](recurring.md).
- `saveTransaction` / `updateTransaction` / `saveTemplate` / `updateTemplate` with a
  `CounterpartyChoice`: resolve the counterparty (create it, or update its defaults) and write the
  row in one transaction.
- `deleteAccount` / `deleteCategory`: reassign or null references, then delete.
  `mergeCounterparties` / `deleteCounterparty` work the same way.
- `clearTransactionsAndTemplates()`: deletes templates first, then transactions.

`TransactionDao.insert` uses `REPLACE`. On the unique index this means delete + re-insert (with a
new id); see #28 A2.

## Dev importer

`ui/dev/DataImporter.kt` + `XlsxReader.kt` do a one-time import of the user's ExpenseLens.xlsx. They
are reachable from Settings and **intentionally kept in release builds** (#29 B2 is won't-do). Imported transactions have
`templateId = null`. `*.xlsx` is gitignored because it holds the user's real financial data.
