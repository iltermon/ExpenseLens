# Sankey Graph Module (:sankey) + Analytics Integration

## Context

A Sankey diagram to visualize transaction cash flow. Requirements agreed in discussion:

- **Separate Gradle module** `:sankey` — a reusable, fully parameterized Sankey library (no hardcoded colors/strings/currency, no app or Room dependency). Hand-rolled Compose Canvas; no third-party chart lib.
- **Two-list API** (typed Kotlin data classes): one list of income entries, one list of output entries. The library derives ribbons itself via proportional allocation; surplus/deficit becomes a synthetic neutral node whose label is a parameter (bilingual app).
- **Three switchable graphs** in the Analytics tab, replacing the existing `GraphPlaceholderCard` ("graph coming soon"): income → **Category**, income → **Account**, income → **Store** (counterparty). Respects the existing period filter (`analyticsItems`).
- **Tap-to-highlight** interactivity (tap node → highlight connected ribbons + show per-link amounts; tap elsewhere → clear).

Verified project facts: single-module project, version catalog at `gradle/libs.versions.toml`, AGP 9.2.1 / Kotlin 2.2.10 / Compose BOM 2026.02.01, `compileSdk { version = release(36) { minorApiLevel = 1 } }`, minSdk 26, Java 11. `app/build.gradle.kts` applies only `android-application` + `kotlin-compose` (+ ksp) — no `kotlin-android` alias exists. No chart code anywhere yet.

## 1. Gradle plumbing

- `gradle/libs.versions.toml`: add plugin `android-library = { id = "com.android.library", version.ref = "agp" }` and library `androidx-compose-foundation = { group = "androidx.compose.foundation", name = "foundation" }` (version via BOM; needed for `Canvas`/`detectTapGestures`).
- `settings.gradle.kts`: `include(":sankey")`.
- `app/build.gradle.kts`: add `implementation(project(":sankey"))`.

## 2. New module `:sankey`

Package `com.iltermon.sankey` (not under `expenselens.*` — it's app-agnostic). No `AndroidManifest.xml` needed (namespace set in Gradle).

```
sankey/
├── build.gradle.kts          # android-library + kotlin-compose plugins; mirrors app's
│                             # compileSdk/minSdk/Java 11 DSL; compose buildFeature;
│                             # deps: compose BOM, ui, ui-graphics, foundation,
│                             # ui-tooling-preview (+ debug tooling), junit (test). NO material3/app.
├── consumer-rules.pro        # empty
└── src/
    ├── main/java/com/iltermon/sankey/
    │   ├── SankeyModel.kt    # SankeyEntry, SankeySide, SankeySelection
    │   ├── SankeyStyle.kt    # SankeyStyle, SankeyDefaults (light/dark palettes)
    │   ├── SankeyLayout.kt   # pure math: LayoutSpec, computeSankeyLayout, hitTestNode
    │   └── SankeyDiagram.kt  # @Composable renderer + gestures + @Previews
    └── test/java/com/iltermon/sankey/
        ├── SankeyAllocationTest.kt
        └── SankeyLayoutTest.kt
```

## 3. Public API

```kotlin
data class SankeyEntry(
    val id: String,            // stable identity for selection
    val label: String,
    val amount: Double,        // >= 0; zero entries dropped
    val color: Color? = null,  // null -> assigned from style.palette
)
enum class SankeySide { SOURCE, TARGET }
data class SankeySelection(val side: SankeySide, val id: String)

@Immutable
data class SankeyStyle(
    val nodeWidth: Dp = 12.dp, val nodeGap: Dp = 10.dp,
    val nodeCornerRadius: Dp = 4.dp, val minNodeHeight: Dp = 4.dp,
    val labelSpacing: Dp = 8.dp, val maxLabelWidthFraction: Float = 0.30f,
    val labelTextStyle: TextStyle, val valueTextStyle: TextStyle,
    val labelColor: Color, val valueColor: Color,
    val palette: List<Color>, val neutralColor: Color,
    val ribbonAlpha: Float = 0.35f, val highlightRibbonAlpha: Float = 0.60f,
    val dimmedAlpha: Float = 0.12f,
    val valueFormatter: (Double) -> String = { "%.2f".format(it) },
)
object SankeyDefaults {
    fun palette(darkTheme: Boolean): List<Color>   // 8 fixed slots, see below
    fun neutral(darkTheme: Boolean): Color         // 0xFF898781
    @Composable fun style(darkTheme: Boolean = isSystemInDarkTheme()): SankeyStyle
}

@Composable
fun SankeyDiagram(
    sources: List<SankeyEntry>,
    targets: List<SankeyEntry>,
    modifier: Modifier = Modifier,
    style: SankeyStyle = SankeyDefaults.style(),
    remainderLabel: String? = null,   // synthetic TARGET node when income > outputs
    deficitLabel: String? = null,     // synthetic SOURCE node when outputs > income
    onSelectionChange: ((SankeySelection?) -> Unit)? = null,
)
```

- Selection is internal `rememberSaveable` state, reset when entry ids change; synthetic nodes get ids `"__remainder__"`/`"__deficit__"` and `neutralColor`.
- Empty side or zero total flow → draws nothing (app shows its own empty state).
- **Before writing renderer/palette code, load the `/dataviz` skill.** Palette (pre-validated categorical set; labels always drawn beside nodes as the contrast relief rule):
  light `2A78D6, 1BAF7A, EDA100, 008300, 4A3AA7, E34948, E87BA4, EB6834`;
  dark `3987E5, 199E70, C98500, 008300, 9085E9, E66767, D55181, D95926`.
  One color index runs across sources then targets so columns don't start on the same hue.

## 4. Layout & allocation algorithm (`SankeyLayout.kt` — pure Kotlin, JVM-testable)

No Compose types (Float/Double + own data classes) so plain JUnit works.

1. **Balance** (in composable, before math): `totalFlow = max(sum(sources), sum(targets))`; pad the smaller side with the remainder/deficit entry so both sides sum to `totalFlow`.
2. **Node heights**: `usable = H − nodeGap·(n−1)`; `h_i ∝ v_i`; iteratively clamp to `minNodeHeight` and rescale the rest. Nodes stacked top-to-bottom **in input order** (callers pre-sort); columns top-aligned.
3. **Links**: proportional allocation `w_ij = v_i · v_j / totalFlow` (row sums = `v_i`, column sums = `v_j` — unit-tested invariants).
4. **Anchors**: on each node edge, link slices tile the node height by *fractional* intervals ordered by opposite index (exact tiling even under min-height clamp).
5. **Ribbons** (draw phase): cubic Bezier closed path — `moveTo(x0,sTop)`, `cubicTo(mx,sTop, mx,tTop, x1,tTop)`, `lineTo(x1,tBottom)`, `cubicTo(mx,tBottom, mx,sBottom, x0,sBottom)`, `close()` with `mx = (x0+x1)/2`.
6. **Labels beside nodes** (not inside — 12dp nodes): source labels right-aligned left of column, targets left-aligned right of column; name line + amount line (amount dropped for very short nodes); greedy downward nudge on collision; ellipsize at `maxLabelWidthFraction`. Text measured via `rememberTextMeasurer` in the composable; resulting plot rect fed into `LayoutSpec` — the math never touches text.
7. **Hit-testing** (`hitTestNode`, pure, tested): node rect ∪ label rect, inflated to a min 48×48px touch target; tap selected node again or empty canvas → clear. Wire via `Modifier.pointerInput(layoutResult) { detectTapGestures { … } }` (tap-only, so the parent `verticalScroll` keeps working).
8. **Highlight rendering** (draw order links → nodes → labels): no selection = ribbons at `ribbonAlpha` in source color; with selection = connected ribbons `highlightRibbonAlpha`, rest `dimmedAlpha`, unconnected nodes dimmed; connected opposite-side nodes show the per-link amount `w_ij` instead of their total. Labels always in `labelColor`/`valueColor`.
9. **Sizing**: track canvas size via `Modifier.onSizeChanged`, compute layout in `remember(entries, size, style)`, shared by draw + pointerInput.

Top-N/"Other" grouping deliberately lives in the **app mapper**, not the library (localized label + app semantics; library stays a dumb renderer).

## 5. App-side mapper — new `app/.../ui/SankeyMapper.kt`

Pure top-level functions + `remember(...)` in the composable (matches the screen's existing aggregation style; the mode toggle is local UI state; moving aggregation to the ViewModel is a separate known refactor, Phase C3).

```kotlin
enum class SankeyOutputMode { CATEGORY, ACCOUNT, COUNTERPARTY }
data class SankeyChartData(val incomes: List<SankeyEntry>, val outputs: List<SankeyEntry>)

fun buildSankeyData(
    items: List<ExpenseItem>, mode: SankeyOutputMode,
    incomeOnlyCategories: Set<String>,
    accountNames: Map<Int, String>, counterpartyNames: Map<Int, String>,
    unassignedLabel: String, otherLabel: String, maxNodesPerSide: Int = 6,
): SankeyChartData
```

Semantics **mirror `netOf()`** (`AnalyticsScreen.kt:49-56`) so the diagram agrees with the SpendingCards:
- Income side (all modes): `!isExpense && category in incomeOnlyCategories`, grouped by category.
- Output side: expenses plus refund-incomes as negatives, grouped by mode key (`category` / `accountNames[accountId] ?: unassignedLabel` / `counterpartyNames[counterpartyId] ?: unassignedLabel`); drop buckets ≤ 0.
- Sort each side descending; fold beyond `maxNodesPerSide` into one `otherLabel` entry (id `"__other__"`).

## 6. AnalyticsScreen integration (`app/.../ui/AnalyticsScreen.kt`)

1. Delete `GraphPlaceholderCard` (lines 331–355); replace the call at line 140 with `SankeyCard(...)`. Collect `viewModel.counterparties` alongside the existing flows (items/accounts/categories already collected at lines 61–66).
2. New private `SankeyCard`: Card titled `analytics_cash_flow`; `SingleChoiceSegmentedButtonRow` with 3 buttons (Category/Account/Store — same pattern as `PeriodHeader`); `mode` in `rememberSaveable`; data via `remember(items, mode, …)` with string params resolved via `stringResource` *before* the remember; empty-state text when both sides empty.
3. Currency formatter: `val symbol = LocalCurrencySymbol.current` then `{ v -> symbol + "%.2f".format(v) }` — the existing `money()` in `ui/CurrencyFormat.kt` is `@Composable` and can't be called inside the lambda.
4. Style: `SankeyDefaults.style(isSystemInDarkTheme()).copy(labelTextStyle = MaterialTheme.typography.labelMedium, valueTextStyle = labelSmall, labelColor = colorScheme.onSurface, valueColor = colorScheme.onSurfaceVariant, valueFormatter = formatter)`.
5. `SankeyDiagram(sources=…, targets=…, remainderLabel = stringResource(analytics_sankey_unspent), deficitLabel = stringResource(analytics_sankey_overspend), modifier = fillMaxWidth().height(chartHeight))`, `chartHeight ≈ (44.dp × max side count + 1).coerceIn(240.dp, 420.dp)`.

## 7. String resources (`res/values/strings.xml` + `res/values-tr/strings.xml`)

Add (TR suggestions — confirm at implementation):
`analytics_cash_flow` Cash flow / Nakit akışı · `analytics_sankey_mode_category` Category / Kategori · `analytics_sankey_mode_account` Account / Hesap · `analytics_sankey_mode_store` Store / Mağaza · `analytics_sankey_unspent` Unspent / Harcanmayan · `analytics_sankey_overspend` Not covered / Karşılanmayan · `analytics_sankey_unassigned` Unassigned / Atanmamış · `analytics_sankey_other` Other / Diğer · `analytics_sankey_empty` No data for this period. / Bu dönemde veri yok.

Remove now-unused `analytics_spending_over_time` and `analytics_graph_coming_soon` from both files (used only by the placeholder).

## 8. Ordered tasks

1. Gradle plumbing (§1) + `sankey/build.gradle.kts` skeleton; verify `./gradlew :sankey:assembleDebug` (JAVA_HOME → JetBrains Runtime).
2. `SankeyModel.kt`, `SankeyStyle.kt` (+ load `/dataviz` before palette/renderer work).
3. `SankeyLayout.kt` pure math.
4. `SankeyAllocationTest.kt` + `SankeyLayoutTest.kt`; `./gradlew :sankey:test`.
5. `SankeyDiagram.kt` renderer + `@Preview`s (light/dark, fake data).
6. Tap-to-highlight interactivity.
7. App strings EN + TR.
8. `SankeyMapper.kt` + `app/src/test/.../SankeyMapperTest.kt`; `./gradlew :app:testDebugUnitTest`.
9. AnalyticsScreen integration.
10. Full build + manual device pass.

## 9. Verification

**Unit tests** — `:sankey`: allocation invariants (row/column sums, remainder/deficit padding, zero-entry drop, empty side); layout (proportional heights, min-height clamp redistribution, exact slice tiling, order preserved); hit-testing (inside rect, inflated region, outside → null). `:app`: mapper grouping per mode, Unassigned buckets, refund netting consistency with `netOf`, top-N folding.

**Manual on device** (confirm before any adb):
1. Analytics tab, month with data → Sankey below the spending cards; side totals agree with summary figures.
2. Switch Category → Account → Store; Unassigned bucket appears for items without account/counterparty.
3. Tap an income node → ribbons brighten, others dim, opposite nodes show per-link amounts; tap again/empty → clears.
4. Income > expenses month → gray "Unspent" node right; expenses > income → gray "Not covered" node left.
5. Empty month → empty-state text; also try income-only and expense-only months.
6. Dark mode → dark palette, readable labels. Turkish locale → all new strings localized.
7. Drag starting on the chart still scrolls the Analytics column.
8. Month/Year period switch updates the chart.
