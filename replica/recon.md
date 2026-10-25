# Recon map: Everly: Recurring Reminders (iOS / iPadOS / macOS) → clone on KMP + Compose Multiplatform (Android + iOS)

Scope: core loop + widgets — recurring habits with N day/week/month/year intervals, doses per day, rolling schedule, scrollable progress grid, per-day notes, reminders, folders, year view, home-screen widgets.
For: a product to publish on Google Play and the App Store under a new brand (Everly has **no Android app** — that is the opening).
Date: 2026-10-25

## Sources

| # | source | URL | notes |
| --- | --- | --- | --- |
| 1 | App Store listing (UZ) | https://apps.apple.com/uz/app/everly-recurring-reminders/id6815256085 | pitch, feature list, What's New for 2026.4/2026.5, privacy labels, platforms. Free, no IAP listed. No ratings yet. |
| 2 | App Store listing screenshots (US) | https://apps.apple.com/us/app/everly-recurring-reminders/id6815256085 | 5 iPhone screenshots: grid, year view, new habit, organize/folders, day note. Saved as reference in `screens/`. |
| 3 | Marketing / support site | https://dollar2048.github.io/everly-site/ | FAQ: iCloud sync, notifications, backup export from Settings. Support via email + GitHub issues. |
| 4 | Privacy policy | https://dollar2048.github.io/everly-site/privacy.html | local-first storage, iCloud private DB, widget shares data, local notifications, TelemetryDeck anonymous analytics, backup export. |
| 5 | Public issue tracker | https://github.com/dollar2048/everly-site/issues | not read yet — worth a pass in /replica-entrepreneur for feature requests. |
| — | help center, pricing page, changelog page, public API | none exist | the app is a solo-dev, free, offline-first app. |
| — | the user's own account | not used | Everly is iOS/Mac only. If you install it on your iPhone, screenshot empty/edge states into `screens/` — that is the biggest gap in this map. |

## Core loop

See at a glance which recurring tasks are due, tap a day to check it off, and have the next due date (and reminder) roll forward from the day you actually did it.

## Screens

| ID | screen | route / how to reach | purpose | key components | states seen |
| --- | --- | --- | --- | --- | --- |
| S01 | Grid (home) | app launch | every habit as a row, days as columns; check off, see what's due | month label header, weekday/date column headers (today highlighted, Sunday tinted), sticky habit column (emoji + name + "every 3 d. · next Oct 18" / "2×/day"), day cells, collapsible folder headers "Home (3)" with colour underline, current-time vertical line through today, bell icon on next reminder day, bottom bar: Today pill, +, ⇅ organize, ⚙ settings | filled (screenshot). Cell states: done, covered-by-interval (soft fill), due (outlined, colour), future/idle (faint), partial dose "1/2", has-note badge, next-reminder bell, tomorrow highlighted for dailies after today is checked. **Not seen:** empty (no habits), overdue, loading. |
| S02 | Habit detail | tap a habit name on S01 (chevron) | history + settings summary for one habit | sheet with ✕ and Edit, Year View card with year stepper (2026 ⌃⌄), GitHub-style year grid (weeks × weekdays, month labels, done days filled in habit colour, today ring, dot on next reminder), rows: Repeat every, Color, Folder, Reminder; Entries list with "+ Add Entry", each row date + note + check | filled. **Not seen:** no entries, other years. |
| S03 | New / Edit habit | + on S01, or Edit on S02 (⌘E on Mac) | create or change a habit | sheet ✕ / ✓, name field (emoji typed in name), "Repeat every" N + −/+ stepper + Day/Week/Month/Year segmented control, doses per day (inferred from "2×/day"), Color: 12 swatches, Folder picker (Ungrouped / existing / create new), Reminder toggle, Reminder Time, Remind ("2 days before" / on the day), Next reminder preview ("Fri, Oct 15 at 9:00 AM"), delete (edit only, guess) | new, reminder on. **Not seen:** validation (empty name), doses control, delete confirm. |
| S04 | Organize | ⇅ on S01 | group and reorder habits | sheet title "Organize", new-folder button, ✕; sections Ungrouped + each folder (folder icon in colour, ⋯ menu); rows: colour dot, name, move-to-folder icon, drag handle | filled. |
| S05 | Day note | long-press a day cell on S01 (or from an entry) | check a day and write what happened | bottom sheet over grid: ✕, habit title, ✓; checkbox + full date; multiline note field | filled, checked. |
| S06 | Settings | ⚙ on S01 | app preferences + data | (inferred) iCloud/sync status, notifications permission, colour theme, export backup, (import backup — guess), about/support, privacy | not seen — guess from FAQ/privacy. |
| S07 | Folder create / edit | new-folder on S04, "new folder" in S03 Folder picker, ⋯ on a folder | name + colour a folder, delete it | dialog/sheet: name, colour, delete | not seen — guess. |
| S08 | Add / edit entry | "+ Add Entry" on S02, or tap an entry row | log a past day with optional note | date picker, done toggle / dose count, note | not seen — guess. Mac: delete entry from context menu. |
| S09 | Widgets | home screen / lock screen / Mac desktop | see due habits and check off without opening the app | interactive widget (check button), lock-screen accessory widget | not seen — listing says "interactive". Sizes unknown. |
| S10 | Notification | system notification on due day or N days before | remind | title = habit name, body likely "due today"/"due in 2 days" | not seen. Actions (mark done from notification) unknown. |
| S11 | Onboarding / empty grid | first launch | explain + first habit + notification permission | unknown | not seen — must design our own. |

## Flows

```
F01 Create a recurring habit
    S01 -> S03 (name, interval N + unit, colour, folder, reminder) -> S01
    happy path clicks: 4 (+ , type name, pick unit, ✓) — 6–7 with reminder on
    edge: empty name, N = 0 / huge, monthly on the 31st, reminder time in the past today,
          notification permission denied, new folder created inline

F02 Check off today (core loop)
    S01 -> tap today's cell
    happy path clicks: 1
    edge: multi-dose (tap increments 1/2 → 2/2; how to undo?), tap again to un-check,
          checking a past day, checking a future day (allowed?), late completion
          → next due re-rolled from completion day, reminder rescheduled

F03 Add a note to a day
    S01 -> long-press cell -> S05 (type, ✓) -> S01
    happy path clicks: 3
    edge: note on an unchecked day, long note, editing/deleting a note

F04 Review a habit's history
    S01 -> tap habit -> S02 (year view, change year, scroll entries) -> ✕
    happy path clicks: 2
    edge: no history, habit created mid-year, year with no entries

F05 Edit or delete a habit
    S01 -> S02 -> Edit -> S03 (change, ✓ / delete + confirm)
    happy path clicks: 4
    edge: changing interval re-computes next due; changing folder; delete keeps/drops entries

F06 Organize into folders
    S01 -> ⇅ -> S04 (drag reorder, move-to-folder, new folder S07, folder ⋯) -> ✕
    happy path clicks: 3–4
    edge: delete a non-empty folder (habits → Ungrouped?), collapse state on S01 persists

F07 Get reminded
    (system) S10 on due day / N days before at reminder time -> tap -> S01 (scrolled to today)
    edge: device reboot, time zone change, DST, app killed, habit checked early
          (cancel pending reminder), Android exact-alarm permission

F08 Check off from a widget
    S09 -> tap check
    happy path clicks: 1
    edge: widget stale after midnight, multi-dose habit, app data changed elsewhere

F09 Back up / move device
    S01 -> S06 -> Export backup -> share sheet / file
    edge: import into a fresh install, version mismatch
    (original relies on iCloud; ours needs its own answer — see Out of scope)

F10 Jump to today
    S01 (scrolled away) -> Today pill
    happy path clicks: 1
```

## Components

| component | variants | states | used on |
| --- | --- | --- | --- |
| Day cell | single-dose, multi-dose (n/m label) | done (solid colour + ✓), partial (n/m), covered (soft tint), due (outlined), idle (faint), today column, has-note badge, next-reminder bell, pressed / long-pressed | S01 |
| Habit row header (sticky) | ungrouped, in folder (left colour bar) | default, pressed; subtitle: "every N u. · next <date>" or "N×/day" | S01 |
| Folder section header | — | expanded / collapsed, count | S01 |
| Day column header | weekday, weekend, today | default, today (highlight), month change | S01 |
| Current-time line | — | today only | S01 |
| Year grid (heatmap) | — | done, empty, today ring, next-reminder dot, out-of-range | S02 |
| Bottom action bar | Today pill + icon group (+, ⇅, ⚙) | default, pressed | S01 |
| Sheet / modal header | ✕ + title + primary (✓ / Edit) | primary enabled / disabled | S02, S03, S04, S05 |
| Grouped form section (inset list) | label row with value, toggle row, picker row, time row | default, disabled | S02, S03, S06 |
| Number stepper | − / + | min reached (− disabled) | S03 |
| Segmented control | Day / Week / Month / Year | selected | S03 |
| Colour swatch picker | 12 colours | selected ring | S03, S07 |
| Reorderable list row | dot + name + move icon + drag handle | dragging | S04 |
| Entry row | date + note + check | done / not done | S02 |
| Note editor | multiline field | empty, filled | S05 |
| Widget | small / medium / lock-screen (sizes guessed) | due items, all done, empty | S09 |

## Inferred data model

```
Folder    id, name, color, sort_order, is_collapsed, created_at, updated_at
          evidence: S01 headers "Home (3)" with colour, S04 sections + ⋯ menu, S03 picker
          confidence: high (fields), medium (is_collapsed persisted)

Habit     id, name (emoji lives inside the name), interval_count (int ≥1),
          interval_unit (day | week | month | year), doses_per_day (int ≥1, default 1),
          color, folder_id (nullable = Ungrouped), sort_order,
          reminder_enabled, reminder_time (local time), remind_days_before (int ≥0),
          created_at, updated_at, (archived_at — guess)
          evidence: S03 form, S01 subtitles "every 3 d.", "2×/day", S02 rows
          confidence: high, except archived_at (guess)

Entry     id, habit_id, date (local calendar date), dose_count (1..doses_per_day),
          note (nullable), created_at, updated_at
          evidence: S02 "Entries" (date + note + check), S05, S01 "1/2" cells
          confidence: high; one row per (habit_id, date) is a guess — could be one row per dose

Settings  theme, (first_weekday?), analytics opt-out?, last_backup_at
          evidence: listing "colour themes", FAQ backup export
          confidence: guess
```

Derived (not stored):

- `last_done_date` = latest Entry.date with dose_count ≥ doses_per_day
- `next_due_date` = last_done_date + interval (rolling; month/year add clamps to month end) — or created_at if never done
- cell state for date d = done | partial | covered (last_done < d < next_due) | due (d == next_due or overdue) | idle
- `next_reminder_at` = next_due_date − remind_days_before at reminder_time

Relationships: Folder 1‑n Habit, Habit 1‑n Entry. Single user, no accounts, local-first.

## Feature matrix

See `features.csv`. Must: 21, should: 12, could: 8, skip: 6.

## Out of scope (cannot or should not be cloned)

- **iCloud / CloudKit sync** — Apple-only; on Android there is no iCloud. Rebuild sync our own way (Google Drive / own backend / file backup) — a decision for /replica-architect.
- **Mac app, iPad split layout, visionOS** — not in this slice; KMP desktop target is a later "could".
- **The name "Everly", its icon, screenshots, marketing copy, colour gradient** — original assets; rebrand in /replica-brand.
- **TelemetryDeck setup / its data** — use your own privacy-friendly analytics if any.
- **Ratings, users, App Store presence** — none to take; the app has no ratings yet.
- **Apple-only APIs** (WidgetKit, Mac keyboard shortcuts) — rebuilt with platform equivalents (Glance on Android, a SwiftUI WidgetKit target on iOS), not copied.

## Size

Screens 11 (5 seen, 6 inferred), flows 10, entities 3 (+ settings).

Hard parts:

1. **The grid** — a 2-axis scroller with a sticky habit column, sticky date header, collapsible folder sections and per-cell states; needs to scroll smoothly through years in Compose Multiplatform (lazy in both directions, shared scroll state between header and rows).
2. **Scheduling logic** — rolling next-due across day/week/month/year with month-end clamping, multi-dose days, time zones/DST, plus **local reminders** rescheduled on every check-off on both Android (AlarmManager exact alarms + boot receiver, POST_NOTIFICATIONS) and iOS (UNUserNotificationCenter, 64-pending limit).
3. **Widgets + sync** — interactive widgets are platform-native on each side (Glance / WidgetKit in Swift reading a shared store), and replacing iCloud with a cross-platform sync/backup story.

Size: **M** (a few weeks) for core loop + widgets + local backup; becomes **L** if cross-device sync between Android and iOS is a must.
