-- Who played each completed game and how players rated each other. Runs after
-- 04-create-game-players.sql (name order).
--
-- Init scripts only run on an empty database. On an existing one run this file once
-- by hand (see the README). It is safe to run again: it only adds what is missing.
--
-- Neither table references games, and game_attendance is separate from game_players:
-- leaving a completed game deletes the game_players row (and the last player leaving
-- deletes the game), but it must not erase the game from a player's score.

CREATE TABLE IF NOT EXISTS game_attendance (
    game_id   INT UNSIGNED NOT NULL,
    user_id   INT UNSIGNED NOT NULL,
    -- the organizer unticked them when marking the game completed
    no_show   BOOLEAN NOT NULL DEFAULT FALSE,
    -- the game's start, a player's score uses their most recent games
    played_at DATETIME NOT NULL,

    PRIMARY KEY (game_id, user_id),
    KEY idx_game_attendance_user (user_id, played_at, game_id),
    CONSTRAINT fk_game_attendance_user FOREIGN KEY (user_id)
        REFERENCES users (user_id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS player_ratings (
    game_id       INT UNSIGNED NOT NULL,
    rater_id      INT UNSIGNED NOT NULL,
    rated_id      INT UNSIGNED NOT NULL,
    punctuality   TINYINT UNSIGNED NOT NULL,
    sportsmanship TINYINT UNSIGNED NOT NULL,
    skill         TINYINT UNSIGNED NOT NULL,
    reliability   TINYINT UNSIGNED NOT NULL,
    overall       TINYINT UNSIGNED NOT NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- a user can only rate a player once per game
    PRIMARY KEY (game_id, rater_id, rated_id),
    -- the ratings a player received
    KEY idx_player_ratings_rated (rated_id, game_id),
    CONSTRAINT chk_player_ratings_range CHECK (
        punctuality BETWEEN 1 AND 5 AND sportsmanship BETWEEN 1 AND 5 AND skill BETWEEN 1 AND 5
        AND reliability BETWEEN 1 AND 5 AND overall BETWEEN 1 AND 5
    ),
    CONSTRAINT fk_player_ratings_rater FOREIGN KEY (rater_id)
        REFERENCES users (user_id) ON DELETE CASCADE,
    CONSTRAINT fk_player_ratings_rated FOREIGN KEY (rated_id)
        REFERENCES users (user_id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- games completed before this script: everyone still in them counts as having shown up
INSERT IGNORE INTO game_attendance (game_id, user_id, played_at)
SELECT p.game_id, p.user_id, g.starts_at
FROM game_players p
JOIN games g ON g.game_id = p.game_id
WHERE g.status = 'completed';
