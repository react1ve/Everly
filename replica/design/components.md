# Components — everly-clone design system

Source: `recon.md` component list and the reference screenshots in `screens/`. Tokens are in `tokens.json` / `tokens.dark.json`. Compose code goes in `AppColors.kt` (generated) and `AppTheme.kt`.

> **Placeholder palette.** At your request, the colours are the original's iOS-system palette (indigo accent, 12 habit colours, grouped grey background). The role names are final. Only the values change in /replica-brand: edit `BASE` and `PALETTE` in `gen_tokens.py`, re-run it, then re-run `contrast.py`. The signature indigo accent combined with the same grid layout is trade dress, so it **must** change before launch.

Rules that apply to every component:

- **Icons:** Lucide (ISC licence) via `com.composables:icons-lucide`, or Material Symbols (Apache 2.0). Never SF Symbols exported as assets, and never the original's icons.
- **Fonts:** the platform default (SF on iOS, Roboto on Android). No licensed font files.
- **Copy:** every label below is a working string written fresh. Final copy comes from /replica-brand's voice guide.
- **Touch targets:** 44dp minimum. **Focus:** 2dp `accent` ring for keyboard / switch access and Mac (later).
- **Motion:** respect reduce-motion. Checks scale 0.85→1 with a spring, sheets use the platform default, nothing loops.

---

## DayCell (the core component)

```
variants  single-dose, multi-dose
size      44×44dp in a 52dp column, radius cell (10)
states    DONE      bg habit.fill, check glyph habit.onFill, 1dp edge habit.outline (light theme only)
          PARTIAL   bg habit.covered, text "n/m" caption/600 habit.ink
          COVERED   bg habit.covered (inside a completed interval)
          DUE       bg habit.idle, 2dp border habit.outline
          OVERDUE   bg habit.idle, 2dp border danger, small "!" badge (our addition)
          IDLE      bg habit.idle
          FUTURE-UNTAPPABLE  same as IDLE/DUE, tap = light haptic, no change
          + overlays: note badge (top-right 12dp speech glyph on surface), next-reminder bell (centre, text-muted),
            today column (column bg today @ 8%), pressed (scale .94), long-press (opens NoteSheet)
tokens    HabitColors for the row's colorKey
a11y      role=checkbox (single) / role=button "Vitamin D, Saturday Oct 17, 1 of 2 doses" (multi);
          state described in words, never by colour alone (check glyph, n/m text, border style differ)
          custom actions: "Add note", "Mark done"
used on   S01
```

## HabitRowHeader (pinned column)

```
size      196dp × 56dp row; 4dp left bar habit.fill when inside a folder
content   name body/500 text (emoji included, 1 line ellipsis) · subtitle footnote habit.ink:
          "every 3 d. · next Oct 18" | "2×/day" | "daily" · trailing chevron text-muted
states    default, pressed (row darkens 4%), archived (60% alpha, later)
tokens    bg habit.row
a11y      role=button "Water the plants, every 3 days, next Oct 18. Opens details"
used on   S01
```

## FolderSectionHeader

```
content   chevron (rotates) · name subhead/600 · "(3)" count · 2dp underline in folder colour, 120dp wide
states    expanded, collapsed, pressed
a11y      role=button, stateDescription expanded/collapsed
used on   S01
```

## DayColumnHeader + MonthLabel

```
content   weekday caption text-muted · date "17" date style text
states    default · weekend (both lines weekend colour) · today (pill bg today @ 12%, text today) · first-of-month (MonthLabel updates)
MonthLabel  headline, pinned above the habit column, follows the leftmost visible date; "↓" jump glyph when today is off-screen
used on   S01
```

## NowLine

```
1dp vertical line in accent @ 50% through the centre of today's column, top to bottom of the grid; today only
used on   S01
```

## BottomActionBar

```
layout    floating, 16dp from the edges, above the system nav/home indicator
left      "Today" pill (surface-raised, text body/500, shadow bar), hidden while today is visible
right     icon group pill: plus (New habit) · arrow-up-down (Organize) · settings (Settings), 44dp each
states    default, pressed, disabled (none)
a11y      each icon has a contentDescription; Today announces "Scroll to today"
used on   S01
```

## SheetHeader

```
layout    ✕ circle button (left, surface-raised) · title (centre) · primary circle (right: accent bg, onAccent check) or "Edit" pill
states    primary enabled / disabled (accent @ 40%, no tap) / loading (spinner, keeps label for screen readers)
used on   S02, S03, S04, S05, S07, S08
```

## GroupedSection + FormRow

```
GroupedSection  surface card, radius card (20), 16dp inner padding horizontally, rows separated by 1dp border inset 16dp;
                optional caption above (subhead text-muted)
FormRow variants
  value     label body · value body text-muted · optional ⌃⌄ glyph (opens menu)
  toggle    label · Switch (Material3 Switch, checked track success)
  time      label · pill chip "9:00 AM" (opens platform time picker)
  action    "+ Add Entry" in accent with plus-circle icon
  textfield single-line input, placeholder text-muted, clear button
states    default, pressed, disabled (text-muted), error (danger caption under the card: "Give it a name")
used on   S02, S03, S06, S07, S08
```

## NumberStepper

```
layout    big number body/600 + unit text-muted ("1 years") · segmented −|+ pill (surface-raised)
range     1..999; − disabled at 1; long-press repeats
a11y      role=adjustable equivalent: semantics setProgress / custom actions increment/decrement, value "every 3 weeks"
used on   S03
```

## SegmentedControl

```
options   Day · Week · Month · Year
states    selected (surface pill on surface-raised track, text/600), unselected (text-muted), pressed
a11y      role=tab/radio group, one selected
used on   S03
```

## ColorSwatchPicker

```
layout    2 rows × 6 swatches 32dp, 16dp gaps, in GroupedSection
states    selected (3dp ring text + 2dp gap), unselected, pressed
a11y      each swatch announces its name ("Teal"), selected state; never colour-only
used on   S03, S07
```

## YearHeatmap

```
layout    53 week columns × 7 weekday rows, 14dp cells 2dp gap, radius 3; month labels caption above; horizontally scrollable,
          starts scrolled to the current month; year switcher "2026 ⌃⌄" in accent
states    done (habit.fill + white day number), covered (habit.covered), empty (border), today (2dp accent ring),
          next reminder (4dp dot text-muted), outside the year (transparent)
a11y      summarized: "2026: 214 days done. Longest gap 9 days." Per-day nodes reachable by explore-by-touch
used on   S02
```

## EntryRow

```
content   date body/600 · note body text-muted (2 lines) · trailing check circle (accent when done, border when note-only)
states    default, pressed (opens S08), swipe-to-delete (Android) / context menu delete (iOS), confirm on delete
used on   S02
```

## ReorderableRow (Organize)

```
content   colour dot 10dp (+1dp edge) · name body · folder-move icon (accent) · drag handle (text-muted)
states    default, dragging (raised, shadow sheet, 1.02 scale), drop target section highlighted
a11y      custom actions "Move up", "Move down", "Move to folder…", because drag alone isn't accessible
lib       sh.calvin.reorderable (Apache 2.0, KMP) or a hand-built LazyColumn drag
used on   S04
```

## NoteSheet

```
layout    bottom sheet, radius sheet; SheetHeader with habit name; checkbox row (habit.fill when checked) + full date;
          multiline TextField (min 3 lines, max 2000 chars, counter after 1800)
states    empty, filled, saving, discard-confirm when closing with edits
used on   S05
```

## EmptyState (we write our own — none seen in the original)

```
content   simple original illustration (to commission) · headline "Nothing repeating yet" · body
          "Add the things that come round — a pill, a filter, an insurance renewal." · primary button "Add your first"
          · 3 template chips (Daily vitamin · Change bedsheets every 2 weeks · Car insurance yearly)
used on   S01 (empty), S11
```

## Button (generic)

```
variants  primary (accent / onAccent), secondary (surface-raised / text), ghost (text accent), danger (danger / white)
sizes     md 44dp, lg 52dp; radius pill
states    default, pressed, focus-visible (2dp ring accent), disabled (40% alpha), loading
used on   S06, S07, S11, dialogs
```

## Dialog / ConfirmDialog

```
Material3 AlertDialog with roles mapped; destructive confirm uses danger text button ("Delete habit and its history?")
used on   S03 delete, S04 delete folder, S06 import (replace), S08 delete entry
```

## Widgets (platform-native, same tokens)

```
Android Glance   small 2×2: next 3 due habits with check buttons · medium 4×2: today row of all due habits
                 colours from GlanceTheme + the same HabitColors values (copied in by gen_tokens.py later)
iOS WidgetKit    systemSmall / systemMedium / accessoryRectangular (lock screen); SwiftUI Color assets generated from tokens
states           due items, all done ("All caught up today"), no habits ("Add a habit in the app"), stale (refreshes at midnight)
```

## Snackbar / toast

```
"Checked — next on Oct 28" after a tap (optional setting), with Undo for 4s. surface-raised, text, accent action
```

---

### Contrast report

`contrast-tokens.txt` and `contrast-tokens.dark.txt`: 73 pairs each, **0 failing AA** in both themes.
Fixes applied in the tokens, not per component:

- `success` darkened (4.40 → passes 4.5).
- Light-theme habit fills that are too pale on white (yellow, mint, green, cyan, teal, orange) get a 1dp `outline` edge on done cells, dots and swatches. The edge is checked at 3:1 against the surface.
- Subtitle text uses a computed `habit.ink` per colour, not the raw fill. Yellow text on a yellow row would be about 1.5:1.
- The check glyph uses `habit.onFill`, picked as black or white per colour. A white check on yellow (the original's look) fails.
