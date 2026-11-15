# Build & test

## Commands

The global `builder` subagent reads this section and runs these commands as written. Keep the
Setup / Build / Test headings, and run every command from the repo root.

### Setup

There is no system JDK on PATH, and `JAVA_HOME` isn't stored in `gradle.properties` or
`local.properties`. Every shell that runs Gradle needs:

```bash
export JAVA_HOME=~/.local/share/JetBrains/Toolbox/apps/rider/jbr   # the webstorm Toolbox app has the same JBR 21
```

### Build

```bash
./gradlew assembleDebug                 # debug APK
```

### Test

```bash
./gradlew test                          # all JVM unit tests (:app + :sankey)
```

## Other commands

```bash
./gradlew :app:compileDebugKotlin       # fastest compile check
./gradlew test --tests "com.iltermon.expenselens.ui.SankeyMapperTest"
./gradlew connectedAndroidTest          # needs a device; ask before any adb/device action
./gradlew lint
```

## Tests

- `app/src/test/.../ui/SankeyMapperTest.kt`: Sankey mapping, including "graph totals == summary
  card totals".
- `app/src/test/.../ui/TransactionItemFilteringTest.kt`: list filter/sort.
- `sankey/src/test/...`: layout and allocation math.
- `app/src/androidTest/.../CategoryIdMigrationTest.kt`: Room migration test.
- `app/src/test/.../data/RecurrenceRangeTest.kt`: finite-series range validation.
- `app/src/test/.../ui/CoreFieldsValidationTest.kt`: trimming of description and amount.
- `app/src/test/.../data/OccurrencesInRangeTest.kt`: recurrence expansion (month-end clamping,
  intervals, units, end cap, range edges, jump-ahead).
- `app/src/test/.../ui/MergeItemsTest.kt`: projection + `(templateId, date)` dedup.
- `app/src/test/.../ui/AnalyticsCalculationsTest.kt`: summary totals and category/account/store rows.
- `app/src/test/.../ui/PeriodSelectionTest.kt`: shared month / custom range.
- `app/src/test/.../ui/TabSectionsTest.kt`: Expenses/Income sections and summary totals.
