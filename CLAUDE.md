# CLAUDE.md

Guidance for Claude Code in this repository. This file is the **index**: keep it short. Detailed
notes live in `docs/claude/`. Read only the topic file you need for the task at hand.

## Maintaining these notes

- Update the relevant `docs/claude/*.md` file in the same change as any code that alters what it
  describes (behavior, invariants, file locations). Add a new topic file instead of growing this
  one, and list it in the index below.
- Keep notes to facts not obvious from the code: invariants, design decisions and their reasons,
  gotchas. Don't duplicate code or git history.

## Project at a glance

ExpenseLens is a personal Android expense/income tracker built with Jetpack Compose, Room,
Navigation Compose, three ViewModels (main, analytics, import), and Coroutines/Flow (MVVM: Room → Repository →
ViewModel StateFlow → Composables). It has two modules: `:app`, and `:sankey`, an app-agnostic
Canvas Sankey library. UI strings are in English (`values/`) and Turkish (`values-tr/`).

Build with JAVA_HOME set to the JetBrains Runtime: `export JAVA_HOME=~/.local/share/JetBrains/Toolbox/apps/rider/jbr`,
then `./gradlew assembleDebug test`. Details are in [build.md](docs/claude/build.md).

## Topic index

| File | Read when working on |
|---|---|
| [build.md](docs/claude/build.md) | Gradle commands, JDK setup, tests |
| [data-layer.md](docs/claude/data-layer.md) | Room entities, DAOs, repository, migrations, settings storage |
| [recurring.md](docs/claude/recurring.md) | Recurring templates, occurrences, auto-pay, dedup, paid/unpaid editing, series delete |
| [ui.md](docs/claude/ui.md) | ViewModels, `ExpenseItem`, navigation, package layout, forms, UI conventions |
| [analytics.md](docs/claude/analytics.md) | Analytics screen, summary totals, Sankey mapping and the `:sankey` module |
| [workflow.md](docs/claude/workflow.md) | Issue tracking, branches, roadmap phases |

## Must-know invariants

- Dates are stored as `LocalDate.toString()` (`YYYY-MM-DD`); month keys use `YearMonth.toString()`.
- A recurring occurrence is "recorded" when a transaction with the same `(templateId, date)`
  exists; a unique DB index enforces this.
- `enableEdgeToEdge()` is on, so bottom UI needs `navigationBarsPadding()`.
- One concept per file; shared composables go in `ui/components/`, never in a screen file
  (rules in [ui.md](docs/claude/ui.md)).
