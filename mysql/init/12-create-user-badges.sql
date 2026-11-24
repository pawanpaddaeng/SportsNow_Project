-- Badges each user has earned. Runs after 01-create-users.sql (name order).
--
-- Init scripts only run on an empty database. On an existing one run this file once
-- by hand (see the README). It is safe to run again: it only adds what is missing.
--
-- A badge is worked out from the user's games, friends and ratings when their badges are
-- read, and saved here the first time they qualify. Once saved it stays, so removing a friend
-- or a game being deleted later doesn't take it away.

CREATE TABLE IF NOT EXISTS user_badges (
    user_id   INT UNSIGNED NOT NULL,
    -- the badge's id, e.g. first_game or mega_mvp (see backend/src/review/badges.ts)
    badge     VARCHAR(32) CHARACTER SET ascii NOT NULL,
    earned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- a user earns each badge once
    PRIMARY KEY (user_id, badge),
    CONSTRAINT fk_user_badges_user FOREIGN KEY (user_id)
        REFERENCES users (user_id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
