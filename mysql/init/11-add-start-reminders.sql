-- Remembers which games already had their "starts soon" reminder. Runs after
-- 03-create-games.sql (name order).
--
-- Init scripts only run on an empty database. On an existing one run this file once
-- by hand (see the README). It is safe to run again: it only adds what is missing.
--
-- The organizer and players of a scheduled game are reminded about an hour before it
-- starts (start_reminder_sent). Moving the start time clears it, so they're reminded
-- again before the new time.

-- ALTER TABLE can't say IF NOT EXISTS, so only run it when the column is missing
SET @add_start_reminder = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'games' AND COLUMN_NAME = 'start_reminder_sent') = 0,
    'ALTER TABLE games
         ADD COLUMN start_reminder_sent BOOLEAN NOT NULL DEFAULT FALSE AFTER rating_reminder_sent,
         ADD KEY idx_games_start_reminder (status, start_reminder_sent, starts_at)',
    'DO 0'
);
PREPARE add_start_reminder FROM @add_start_reminder;
EXECUTE add_start_reminder;
DEALLOCATE PREPARE add_start_reminder;

-- games that started before this script never need a reminder
UPDATE games SET start_reminder_sent = TRUE
WHERE start_reminder_sent = FALSE AND starts_at <= UTC_TIMESTAMP();
