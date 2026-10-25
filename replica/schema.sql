-- Schema for the Everly-style recurring-habits clone.
-- Dialect: SQLite, written to drop straight into SQLDelight (shared/data/src/commonMain/sqldelight/.../Habit.sq).
-- Local-first, single user, no accounts in v1. Every table is sync-ready:
--   * TEXT uuid ids (generated on device, so two devices never collide)
--   * created_at / updated_at as INTEGER epoch millis, UTC
--   * deleted_at tombstones instead of hard deletes, so a later sync can propagate deletes
-- Calendar days (check-ins, anchors) are TEXT 'yyyy-MM-dd' LOCAL dates on purpose:
-- a check-in is "Saturday Oct 17", not an instant. Converting it to UTC breaks it on travel / DST.
-- Access rules: none in SQL. The DB lives in the app sandbox (and the iOS App Group shared with the widget).
-- Every read in the data layer filters deleted_at IS NULL. If sync ships, the Postgres mirror gets
-- user_id + RLS (see architecture.md, "Sync v2").

PRAGMA foreign_keys = ON;
-- Set from the driver on open: PRAGMA journal_mode = WAL;  (app + widget extension read/write concurrently)

CREATE TABLE folder (
  id            TEXT    NOT NULL PRIMARY KEY,
  name          TEXT    NOT NULL CHECK (length(trim(name)) BETWEEN 1 AND 60),
  color_key     TEXT    NOT NULL,                      -- token key from the design system, e.g. 'teal'; not a hex, so themes can remap it
  sort_order    INTEGER NOT NULL,
  is_collapsed  INTEGER NOT NULL DEFAULT 0 CHECK (is_collapsed IN (0, 1)),
  created_at    INTEGER NOT NULL,
  updated_at    INTEGER NOT NULL,
  deleted_at    INTEGER
);
CREATE INDEX folder_active_order ON folder (sort_order) WHERE deleted_at IS NULL;
CREATE INDEX folder_updated      ON folder (updated_at);   -- sync cursor

CREATE TABLE habit (
  id                      TEXT    NOT NULL PRIMARY KEY,
  name                    TEXT    NOT NULL CHECK (length(trim(name)) BETWEEN 1 AND 80),   -- emoji lives inside the name
  interval_count          INTEGER NOT NULL DEFAULT 1 CHECK (interval_count BETWEEN 1 AND 999),
  interval_unit           TEXT    NOT NULL DEFAULT 'DAY' CHECK (interval_unit IN ('DAY', 'WEEK', 'MONTH', 'YEAR')),
  doses_per_day           INTEGER NOT NULL DEFAULT 1 CHECK (doses_per_day BETWEEN 1 AND 12),
  anchor_date             TEXT    NOT NULL,           -- first due date (local) while there is no completion yet
  color_key               TEXT    NOT NULL,
  -- folder deleted (hard purge) -> habit falls back to Ungrouped. Soft delete is handled in the data layer the same way.
  folder_id               TEXT    REFERENCES folder (id) ON DELETE SET NULL,
  sort_order              INTEGER NOT NULL,
  reminder_enabled        INTEGER NOT NULL DEFAULT 0 CHECK (reminder_enabled IN (0, 1)),
  reminder_minute_of_day  INTEGER NOT NULL DEFAULT 540 CHECK (reminder_minute_of_day BETWEEN 0 AND 1439),  -- local wall clock, 540 = 09:00
  remind_days_before      INTEGER NOT NULL DEFAULT 0 CHECK (remind_days_before BETWEEN 0 AND 30),
  archived_at             INTEGER,
  created_at              INTEGER NOT NULL,
  updated_at              INTEGER NOT NULL,
  deleted_at              INTEGER
);
CREATE INDEX habit_active_folder_order ON habit (folder_id, sort_order) WHERE deleted_at IS NULL;
CREATE INDEX habit_reminders           ON habit (reminder_enabled)       WHERE deleted_at IS NULL AND archived_at IS NULL;
CREATE INDEX habit_updated             ON habit (updated_at);

CREATE TABLE entry (
  id          TEXT    NOT NULL PRIMARY KEY,
  -- habit purged -> its history goes with it. (Normal deletes are tombstones; purge happens later.)
  habit_id    TEXT    NOT NULL REFERENCES habit (id) ON DELETE CASCADE,
  date        TEXT    NOT NULL CHECK (date GLOB '[0-9][0-9][0-9][0-9]-[0-1][0-9]-[0-3][0-9]'),
  dose_count  INTEGER NOT NULL DEFAULT 1 CHECK (dose_count BETWEEN 0 AND 12),  -- 0 = note only, not checked
  note        TEXT    CHECK (note IS NULL OR length(note) <= 2000),
  created_at  INTEGER NOT NULL,
  updated_at  INTEGER NOT NULL,
  deleted_at  INTEGER,
  -- The hard constraint: one row per habit per day. Double taps, the widget and the app racing,
  -- or a notification action all UPSERT into the same row instead of creating duplicates.
  CONSTRAINT one_entry_per_day UNIQUE (habit_id, date)
);
CREATE INDEX entry_date    ON entry (date);       -- grid / widget range scans across all habits
CREATE INDEX entry_updated ON entry (updated_at);
-- (habit_id, date) is already indexed by the UNIQUE constraint.

-- Settings (theme, first weekday, analytics opt-out, last_backup_at, onboarding_done) are NOT a table:
-- multiplatform-settings key-value store. Nothing relational about them.

-- ---------------------------------------------------------------------------
-- Core queries (SQLDelight labels). The data layer exposes these as Flows.
-- ---------------------------------------------------------------------------

-- selectActiveHabits:
-- SELECT h.*, f.sort_order AS folder_order
-- FROM habit h LEFT JOIN folder f ON f.id = h.folder_id AND f.deleted_at IS NULL
-- WHERE h.deleted_at IS NULL AND h.archived_at IS NULL
-- ORDER BY (h.folder_id IS NOT NULL), folder_order, h.sort_order;

-- selectEntriesInRange:   (grid window, e.g. today-60 .. today+30, plus the last completion before the window per habit)
-- SELECT * FROM entry WHERE deleted_at IS NULL AND date BETWEEN :from AND :to;

-- selectLastCompletionBefore:
-- SELECT e.habit_id, max(e.date) AS last_done
-- FROM entry e JOIN habit h ON h.id = e.habit_id
-- WHERE e.deleted_at IS NULL AND e.dose_count >= h.doses_per_day AND e.date < :from
-- GROUP BY e.habit_id;

-- upsertEntry:   (idempotent check-off)
-- INSERT INTO entry (id, habit_id, date, dose_count, note, created_at, updated_at, deleted_at)
-- VALUES (:id, :habitId, :date, :doseCount, :note, :now, :now, NULL)
-- ON CONFLICT (habit_id, date) DO UPDATE SET
--   dose_count = excluded.dose_count, note = excluded.note, updated_at = excluded.updated_at, deleted_at = NULL;

-- selectEntriesForYear:  (S02 year view + entries list)
-- SELECT * FROM entry WHERE habit_id = :habitId AND deleted_at IS NULL AND date BETWEEN :yearStart AND :yearEnd ORDER BY date DESC;
