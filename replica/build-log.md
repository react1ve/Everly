# Build log

Status words: done = built + verified on device; partial = built, something missing or unverified.

| date | ID | status | missing / notes | harder than expected |
| --- | --- | --- | --- | --- |
| 2026-10-25 | M0 shell | partial | Gradle KMP project (:core, :shared, :androidApp) + iosApp via XcodeGen, Koin, theme from replica/design, routes for S01–S08 + design catalog, seed data. **Not compiled yet**: the build sandbox can't reach Maven/Google/Gradle (org network policy), so the first build happens on the Mac. | Copied the AGP 9 / KMP plugin setup from atlanta_kmp to avoid config drift. |
| 2026-10-25 | core | partial | ScheduleEngine: rolling next-due, month-end clamp, doses, cell states incl. OVERDUE, next reminder. 17 unit tests written, not yet run. | Decided a rule: once a reminder is overdue it repeats daily at the same time (architecture.md said "no nagging"). This changes architecture.md. |
| 2026-10-25 | data | partial | `HabitRepository` interface + `SnapshotHabitRepository` (JSON in multiplatform-settings), 6 tests. Stand-in until /replica-backend swaps in SQLDelight with the same interface. Folders persisted but not editable yet. | — |
| 2026-10-25 | S01 Grid | partial | Pinned habit column, date header, horizontal scroll with only visible columns composed, all cell states, today highlight, Today button, bottom bar, empty state with templates. Missing: folder sections + collapse (M2), now-line (M3), long-press note opens a stub. No screenshot yet for diff. | Pinned 2-axis grid: built as one shared horizontal offset plus a virtualised strip per row, instead of syncing many LazyRows. |
| 2026-10-25 | S03 New/Edit | partial | Name, repeat N + unit, times per day, colour, delete with confirm. Missing: folder picker, reminder fields, next-reminder preview (M2). Full screen, not a bottom sheet yet. | — |
| 2026-10-25 | S02 S04 S05 S06 S07 S08 | stub | Routed stub screens; S02 links to Edit, S06 links to the design catalog. | — |

Deviations from architecture.md
- Modules: `:shared:data/ui/platform` are packages inside `:shared` for now. `:core` is a separate module because the iOS widget needs to link it without Compose.
- Persistence: snapshot JSON instead of SQLDelight for the vertical slice (replica-build rule: fake data layer with the same signatures).
- Icons: Material Symbols (Apache 2.0, via compose material-icons-extended) instead of Lucide, to avoid another dependency during the slice.

Before release: `SEED_SAMPLE_DATA=false`, real applicationId/bundle id, rebranded palette, app name.
