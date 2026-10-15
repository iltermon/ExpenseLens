# Build & test

There is no system JDK on PATH. Every shell that runs Gradle needs:

```bash
export JAVA_HOME=~/.local/share/JetBrains/Toolbox/apps/rider/jbr   # the webstorm Toolbox app has the same JBR 21
```

This isn't stored in `gradle.properties` or `local.properties`.

```bash
./gradlew assembleDebug                 # debug APK
./gradlew :app:compileDebugKotlin       # fastest compile check
./gradlew test                          # all JVM unit tests (:app + :sankey)
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
