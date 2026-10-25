# Build log

Status words: done = built + verified on device; partial = built, something missing or unverified.

| date | ID | status | missing / notes | harder than expected |
| --- | --- | --- | --- | --- |
| 2026-10-25 | M0 shell | partial | Gradle KMP project (:core, :shared, :androidApp) + iosApp via XcodeGen, Koin, theme from replica/design, routes for S01–S08 + design catalog, seed data. **Not compiled yet**: the build sandbox can't reach Maven/Google/Gradle (org network policy), so the first build happens on the Mac. | Copied the AGP 9 / KMP plugin setup from atlanta_kmp to avoid config drift. |
| 2026-10-25 | core | partial | ScheduleEngine: rolling next-due, month-end clamp, doses, cell states incl. OVERDUE, next reminder. 17 unit tests written, not yet run. | Decided a rule: once a reminder is overdue it repeats daily at the same time (architecture.md said "no nagging"). This changes architecture.md. |
| 2026-10-25 | data | partial | `HabitRepository` interface + `SnapshotHabitRepository` (JSON in multiplatform-settings), 6 tests. Stand-in until /replica-backend swaps in SQLDelight with the same interface. Folders persisted but not editable yet. | — |
| 2026-10-25 | S01 Grid | partial | Pinned habit column, date header, horizontal scroll with only visible columns composed, all cell states, today highlight, Today button, bottom bar, empty state with templates. Missing: folder sections + collapse (M2), now-line (M3), long-press note opens a stub. No screenshot yet for diff. | Pinned 2-axis grid: built as one shared horizontal offset plus a virtualised strip per row, instead of syncing many LazyRows. |
| 2026-10-25 | S03 New/Edit | partial | Name, repeat N + unit, times per day, colour, delete with confirm. Missing: folder picker, reminder fields, next-reminder preview (M2). Full screen, not a bottom sheet yet. | — |
| 2026-10-25 | S02 S04 S05 S06 S07 S08 | stub | (superseded by M2 below) | — |
| 2026-10-25 | M2 data | partial | Folder CRUD + collapse, deleteEntry; 2 more repo tests. | — |
| 2026-10-25 | M2 reminders | partial | `ReminderScheduler` interface; `ReminderCoordinator` keeps one pending reminder per habit (soonest 60), re-syncs on data change + day rollover. Android: AlarmManager exact/inexact fallback, POST_NOTIFICATIONS via an Activity bridge, receivers for fire / boot / time / zone / update / exact-alarm permission. iOS: UNUserNotificationCenter calendar triggers. Missing: iOS background top-up (the daily overdue nag stops if the app isn't opened), notification "mark done" action, battery-optimisation hint. | Android permission request from shared code needed a small bridge object owned by MainActivity. |
| 2026-10-25 | S01 Grid | partial | + folder sections (collapse on tap, edit on long-press), folder colour bar on rows, long-press opens S05. Still missing: now-line, bell refinements (M3). | Removed a compile bug from M1: enum `Template` had a `name` property, which clashes with `Enum.name`. |
| 2026-10-25 | S02 Habit detail | partial | Year heatmap (prev/next year), summary rows, next due, reminder + next reminder, entries list for the year, add/edit entry. Missing: year picker menu (arrows instead). | — |
| 2026-10-25 | S03 New/Edit | partial | + folder picker with "New folder…", reminder toggle/time/days-before, next reminder preview, permission warning. Still a full screen, not a sheet. | — |
| 2026-10-25 | S05 / S08 Entry sheet | partial | One ModalBottomSheet for both: done toggle, note (2000 chars), date picker for new entries (past/today only), delete, discard confirm. Picking a date that already has an entry overwrites that day's note. | — |
| 2026-10-25 | S07 Folder | partial | Name, colour, delete with "habits stay" confirm. | — |
| 2026-10-25 | M2 fix | done | Missing `Modifier` import in FolderScreen.kt (fixed by Mukhammad). Added an import checker to the pre-sync routine. | — |
| 2026-10-25 | M3 data | partial | shiftHabit / shiftFolder / moveHabit, versioned JSON backup export + import (replace, rejects bad/newer), deleteAll; 4 more repo tests (12 total). `AppSettings` for theme. | — |
| 2026-10-25 | S04 Organise | partial | Sections per folder, ↑/↓ for habits and folders, move-to-folder menu, new/edit folder, accessibility custom actions. Drag-and-drop not built (avoided a new dependency). | — |
| 2026-10-25 | S06 Settings | partial | Theme Auto/Light/Dark, notification status + ask, export / restore backup, delete everything, version, design catalog. Missing: battery-optimisation hint, 12/24h. | Backups need platform pickers: SAF launchers owned by MainActivity on Android; share sheet + document picker on iOS. |
| 2026-10-25 | S09 Widget | partial | Android Glance "Today" widget: due/overdue/partial first, tap a square to check off, refreshed on every data change + every 30 min. **iOS WidgetKit not built** (needs a Swift extension target + App Group storage; next). | — |
| 2026-10-25 | S01 Grid | partial | + now-line through today's column. | — |
| 2026-10-25 | Android finish | partial | Notification "Done" action (markDone, idempotent) + tap opens the habit (DeepLinks); Settings rows for exact alarms + battery optimisation (SystemSettings, re-checked on resume); drag-handle reorder in Organise with haptics; haptic tick on check-off; widget sizes (small/medium/tall) + "N to do"; placeholder adaptive launcher icon; release build with R8 + resource shrinking. 13 repo tests. Not yet built on device. | Reviewer caught a wrong `zIndex` import before sync. |

Deviations from architecture.md
- Modules: `:shared:data/ui/platform` are packages inside `:shared` for now. `:core` is a separate module because the iOS widget needs to link it without Compose.
- Persistence: snapshot JSON instead of SQLDelight for the vertical slice (replica-build rule: fake data layer with the same signatures).
- Icons: Material Symbols (Apache 2.0, via compose material-icons-extended) instead of Lucide, to avoid another dependency during the slice.

Before release: `SEED_SAMPLE_DATA=false`, real applicationId/bundle id, rebranded palette, app name.
