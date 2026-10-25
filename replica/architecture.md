# Architecture: working title "everly-clone" (a rebuild of Everly's core features, Android + iOS)

Inputs: `recon.md`, `features.csv`. Schema: `schema.sql`. Real name comes from /replica-brand.

## Decisions up front

- **Local-first, no backend in v1.** Everly has no server, no accounts and no payments. Neither does v1. That removes auth, email, hosting and webhooks from the critical path.
- **Backup = JSON file export/import** (feature rows "Export backup", "Import backup"). That covers "moving to a new phone".
- **Cross-device sync is v2** ("could" in features.csv). The schema is already sync-ready (uuid ids, `updated_at`, tombstones), so adding it later does not need a migration of existing data.
- **Monetization is decided in /replica-launch**, not here. If it becomes a paid tier, use RevenueCat KMP; nothing in this plan blocks it.

## Stack

Matched to your existing KMP projects (atlanta_kmp versions), so nothing new to learn except SQLDelight and Glance.

| layer | choice | why |
| --- | --- | --- |
| language / targets | Kotlin 2.4, KMP: `androidTarget`, `iosArm64`, `iosSimulatorArm64` | one codebase for logic and UI; desktop target can be added later |
| UI | Compose Multiplatform 1.11 + Material 3 | shared screens on both platforms; iOS hosts it in `ComposeUIViewController` |
| navigation | `org.jetbrains.androidx.navigation:navigation-compose` + M3 modal bottom sheets | type-safe routes; S02–S05 are sheets over S01 |
| DI | Koin 4.2 | already your default |
| database | SQLDelight 2 (SQLite), `android-driver` / `native-driver` | SQL is the source of truth (`schema.sql`), Flow queries, migrations as `.sqm`. Swap: Room KMP |
| dates | kotlinx-datetime | `LocalDate.plus(DatePeriod)` clamps month ends for us |
| settings | multiplatform-settings | theme, first weekday, flags |
| serialization | kotlinx-serialization-json | backup file format |
| logging | Kermit | already used |
| notifications | expect/actual `ReminderScheduler`: Android `AlarmManager` + `NotificationCompat`; iOS `UNUserNotificationCenter` | both are local, on-device, like the original |
| widgets | Android: Jetpack Glance (Kotlin, reads the same DB). iOS: WidgetKit extension in Swift + AppIntents, linking the shared KMP framework; DB file in an App Group | no shared widget API exists; logic stays shared |
| file export | Android: Storage Access Framework `CreateDocument`; iOS: `UIDocumentPickerViewController` / share sheet | user picks where the backup goes |
| analytics (optional) | none in v1; later a privacy-first one (e.g. PostHog EU with no PII, or TelemetryDeck-like counts) | matches the privacy pitch |
| CI | GitHub Actions: `./gradlew check` + iOS framework build on macOS runner | tests for the schedule engine run on every push |
| distribution | Play Console internal track, TestFlight | /replica-deploy |

## Modules

```
:shared:core        pure Kotlin. Models + ScheduleEngine (next due, cell states, next reminder). No platform deps. 100% unit tested.
:shared:data        SQLDelight DB, repositories, BackupCodec (JSON), Koin modules
:shared:ui          design system (tokens from /replica-design), screens S01–S08, ViewModels (androidx lifecycle-viewmodel KMP)
:shared:platform    expect/actual: ReminderScheduler, WidgetRefresher, FileExporter, PermissionGate, Clock/TimeZone source
:androidApp         MainActivity, Glance widget + ActionCallback, BootReceiver, AlarmReceiver
iosApp/             SwiftUI host, WidgetKit extension (Swift), AppIntent "ToggleDayIntent", App Group entitlements
```

## Schema

Tables: **3** (`folder`, `habit`, `entry`) + key-value settings. Access rules: none needed in SQL in v1 (single user, sandboxed file); every data-layer read filters `deleted_at IS NULL`. Full SQL in `schema.sql`.

Key choices:

- `entry` has `UNIQUE (habit_id, date)`, and every check-off is an UPSERT. That is how double taps, the widget and the app writing at the same time stay consistent. It's a database rule, not something the UI has to enforce.
- Check-in days are `TEXT yyyy-MM-dd` **local dates**, not timestamps. Instants (`created_at`, `updated_at`) are UTC epoch millis.
- Reminder time is `reminder_minute_of_day` (wall clock), so 09:00 stays 09:00 after a time-zone change.
- Colours are stored as design-token keys (`'teal'`), not hex, so themes and dark mode can remap them.
- `on delete`: `habit.folder_id → SET NULL` (habit drops to Ungrouped), `entry.habit_id → CASCADE` (history goes with the habit). Normal deletes are tombstones. Hard purge only runs after sync is confirmed, or after 30 days if sync never ships.

### ScheduleEngine (the heart of the app, `:shared:core`)

```
done(day)        = entry.dose_count >= habit.doses_per_day
lastDone         = max(date where done)
nextDue          = lastDone?.plus(interval) ?: habit.anchorDate        // rolling schedule
interval         = DAY→N days, WEEK→7N days, MONTH→N months, YEAR→N years (month-end clamp by kotlinx-datetime)
cellState(day)   = DONE | PARTIAL(n/m) | COVERED (prevDone < day < prevDone+interval)
                 | DUE (day == nextDue) | OVERDUE (nextDue < today, day in nextDue..today) | IDLE
nextReminderAt   = (nextDue - remindDaysBefore) at reminderMinuteOfDay, local zone
                   if that instant is already past and nextDue >= today → nextDue at reminder time
                   if that is past too → none until the next check-off (no nagging in v1)
```

Rules decided here (the original's behaviour isn't confirmed for all of them):

- Tapping a cell cycles doses: 0 → 1 → … → doses_per_day → 0. Single-dose: toggle.
- Today and past days can be checked. Future days can't (haptic + no-op). They only show DUE, COVERED or the reminder bell.
- `OVERDUE` is our addition. The original seems to show nothing special when you're late. Check that in /replica-entrepreneur.

## API (the in-app use cases — there is no server)

"Who" = the app process (A) and/or the widget process (W).

| operation | does | who | input | output | flow |
| --- | --- | --- | --- | --- | --- |
| `observeGrid(from, to)` | habits + folders + entries in window → rows of cell states | A | date range | `Flow<GridState>` | F02, F10 |
| `toggleDay(habitId, date)` | cycle dose count via UPSERT, then reschedule reminder + refresh widgets | A, W | ids | new cell state | F02, F08 |
| `setNote(habitId, date, note, checked)` | UPSERT entry with note | A | text | — | F03 |
| `createHabit(draft)` | validate, insert with anchor = today (or chosen), schedule reminder | A | HabitDraft | id | F01 |
| `updateHabit(id, draft)` | update, recompute next due + reminder | A | HabitDraft | — | F05 |
| `deleteHabit(id)` | tombstone habit + entries, cancel reminder | A | id | — | F05 |
| `previewNextReminder(draft)` | ScheduleEngine only, no write | A | HabitDraft | instant? | F01 |
| `observeHabitDetail(id, year)` | habit + year heatmap + entries list | A | id, year | Flow | F04 |
| `addEntry / updateEntry / deleteEntry` | past-day logging from S02/S08 | A | entry | — | F04 |
| `createFolder / renameFolder / recolorFolder / deleteFolder` | folder CRUD; delete moves habits to Ungrouped | A | folder | id | F06 |
| `setFolderCollapsed(id, bool)` | persist section state | A | — | — | F06 |
| `reorderHabits(folderId, orderedIds)` | rewrite sort_order in one transaction | A | ids | — | F06 |
| `moveHabit(habitId, folderId?)` | change folder, append at end | A | ids | — | F06 |
| `observeWidgetSnapshot(today)` | today's due/partial habits for widgets | W | date | list | F08 |
| `exportBackup()` | JSON `{schemaVersion, exportedAt, folders, habits, entries}` → user-picked file | A | — | file | F09 |
| `importBackup(file, mode)` | validate version; **replace** (v1, with confirm) | A | file | counts | F09 |
| `rescheduleAll()` | cancel + rebuild every reminder | A, system | — | — | F07 |

Webhooks in/out: none.

Jobs:

| job | trigger | does |
| --- | --- | --- |
| Reminder schedule | after every write touching a habit or entry | one pending alarm/notification per habit, deterministic id from habit id |
| Android reschedule | `BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED`, `MY_PACKAGE_REPLACED` | `rescheduleAll()` (alarms don't survive reboot) |
| iOS top-up | app foreground + `BGAppRefreshTask` | keep the soonest ≤ 60 requests pending (the iOS limit is 64) |
| Midnight rollover | Android: alarm at next local midnight → `GlanceAppWidget.updateAll`; iOS: timeline entry at midnight | widgets and "today" never go stale |
| Tombstone purge | app start, weekly | hard-delete rows with `deleted_at` older than 30 days (sync-off only) |

### Sync v2 (not built now, the plan so the schema stays honest)

Supabase Postgres mirrors the 3 tables plus `user_id uuid not null`, with RLS `using (user_id = auth.uid())`. Sign in with Apple / Google through Supabase Auth. Pull and push by an `updated_at` cursor, last write wins per row, and deletes travel as tombstones. `entry`'s unique `(user_id, habit_id, date)` makes merges idempotent. Moves the size from M to L.

## The parts that bite

- **Time zones / DST:** days are local dates and reminder times are wall-clock minutes, so a flight or a DST change doesn't move a check-in. `rescheduleAll()` runs when the zone changes. DST-gap times (e.g. 02:30) roll to the next valid minute.
- **Month arithmetic:** monthly from Jan 31 → Feb 28 (29 in leap years). Rolling from the completion date resets any drift. Unit tests cover leap years, the 29th/30th/31st and `interval_count` > 12.
- **Idempotency / races:** the `UNIQUE(habit_id, date)` UPSERT. The widget and app on iOS share the SQLite file in an App Group with WAL mode and a busy timeout. Rapid taps are debounced in the ViewModel, and the DB stays correct anyway.
- **Android notification permissions:** `POST_NOTIFICATIONS` (13+), asked when the first reminder is enabled, not on launch. Exact alarms: `SCHEDULE_EXACT_ALARM` needs the user's approval on 14+. Fall back to `setAndAllowWhileIdle` (a few minutes late) and show a "reminders may be late" row in Settings. Some vendors (Xiaomi, Huawei…) kill alarms, so add a battery-optimisation hint in Settings.
- **iOS 64-notification cap:** one per habit plus top-up covers it until a user has more than 60 reminded habits. Past that, the soonest win.
- **Grid performance:** a 2D lazy layout. Habit column pinned, date header pinned, and the horizontal scroll state is shared between the header and every row (one `LazyListState` driving all rows, or a custom `LazyLayout`). Cell states are computed off the main thread in the ViewModel for a sliding window (±90 days) and extended when you scroll near the edge. This needs a prototype in M1, not M3.
- **Widget memory (iOS):** widget extensions have a ~30 MB limit. Keep the KMP framework that the widget links free of Compose (`:shared:core` + `:shared:data` only, as a separate framework).
- **Offline:** always. Nothing to do.
- **Search, realtime, rate limits, email, multi-tenancy:** not applicable in v1.
- **GDPR / deletion:** data never leaves the device in v1. "Delete all data" in Settings wipes the DB and cancels every alarm. The store privacy label can truthfully say "data not collected" if analytics stays off.

## Build order

1. **M0 – skeleton (1–2 days).** KMP project from your template, Koin, SQLDelight wired on both platforms, CI, empty theme. Tables: all 3 (migration 1). Gets the iOS host app running.
2. **M1 – vertical slice (≈1 week), ugly on purpose.**
   Screens: S01 grid (pinned column + horizontal scroll, no folders yet), S03 minimal (name + N + unit).
   Tables: habit, entry. Ops: `createHabit`, `observeGrid`, `toggleDay`.
   `ScheduleEngine` with full unit tests. Runs on an Android phone and an iPhone. Proves the grid scrolls well and the rolling schedule is right.
3. **M2 – must-haves (≈1.5–2 weeks).**
   - Grid: every cell state, today highlight + Today button, collapsible folder sections, note badge.
   - Habits: doses per day, colours, edit/delete (S03 full).
   - Notes: S05 long-press.
   - History: S02 detail + year heatmap, S08 entries.
   - Folders: S07 CRUD.
   - Reminders: scheduler on both platforms + Android receivers.
4. **M3 – should-haves (≈1–1.5 weeks).** Organize S04 (drag reorder, move, inline new folder), Android Glance widget then iOS widget, backup export/import, Settings S06 with permission status, themes + dark mode, bell / time line / tomorrow highlight, next-reminder preview.
5. **M4 – could-haves.** Localization (EN, RU, UZ first), mark-done notification action, onboarding + templates, analytics, Compose Desktop, Sync v2.
6. **M5 – fixes from /replica-entrepreneur**, slotted in by priority once it has run.

Total for M0–M3: about 4–6 weeks of part-time work for one senior KMP engineer. That's in line with size M in recon.md.
