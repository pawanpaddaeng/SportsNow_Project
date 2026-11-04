-- Chats between two friends, in the same tables as game chats. Runs after
-- 06-create-chats.sql and 07-create-friends.sql (name order).
--
-- Init scripts only run on an empty database. On an existing one run this file once
-- by hand (see the README). It is safe to run again: it only adds what is missing.
--
-- A chat is either a game's (game_id set) or two friends' (user_low_id and user_high_id
-- set, lowest id first so a pair has one row). Who can read a friend chat comes from
-- friendships, so it isn't stored here. There is no CHECK on the pair because MySQL
-- doesn't allow one on columns with ON DELETE CASCADE, createFriendChat keeps it in order.

-- ALTER TABLE can't say IF NOT EXISTS, so only run it when the columns are missing
SET @add_friend_columns = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chats' AND COLUMN_NAME = 'user_low_id') = 0,
    'ALTER TABLE chats
         MODIFY game_id INT UNSIGNED NULL,
         ADD COLUMN user_low_id INT UNSIGNED NULL AFTER game_id,
         ADD COLUMN user_high_id INT UNSIGNED NULL AFTER user_low_id,
         ADD UNIQUE KEY uq_chats_friends (user_low_id, user_high_id),
         ADD CONSTRAINT fk_chats_user_low FOREIGN KEY (user_low_id)
             REFERENCES users (user_id) ON DELETE CASCADE,
         ADD CONSTRAINT fk_chats_user_high FOREIGN KEY (user_high_id)
             REFERENCES users (user_id) ON DELETE CASCADE',
    'DO 0'
);
PREPARE add_friend_columns FROM @add_friend_columns;
EXECUTE add_friend_columns;
DEALLOCATE PREPARE add_friend_columns;

-- friends from before this script have no chat yet. A friendship is two rows, this reads one of them
INSERT IGNORE INTO chats (user_low_id, user_high_id)
SELECT user_id, friend_id FROM friendships WHERE user_id < friend_id;
