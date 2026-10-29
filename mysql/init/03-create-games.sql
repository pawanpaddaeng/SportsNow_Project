-- Games organized by users, as shown on the Game Details screen. Every game
-- needs the organizer's approval to join. Runs after 01-create-users.sql
-- (name order) since it references users.
--
-- Times are stored in UTC; the app converts them to the user's time zone.

CREATE TABLE IF NOT EXISTS games (
    game_id       INT UNSIGNED NOT NULL AUTO_INCREMENT,
    -- user who created the game and approves join requests
    organizer_id  INT UNSIGNED NOT NULL,
    title         VARCHAR(100) NOT NULL,
    -- e.g. basketball, soccer, volleyball; checked by the backend
    sport         VARCHAR(50) NOT NULL,
    -- optional "About" text, e.g. what to bring or the format
    description   VARCHAR(1000) NULL,

    -- when the game happens (UTC). ends_at is optional for open-ended games
    starts_at     DATETIME NOT NULL,
    ends_at       DATETIME NULL,

    -- place picked on the map: display name, full address and coordinates
    -- (coordinates are used for the map pin and sorting Discover by distance)
    location_name VARCHAR(255) NOT NULL,
    address       VARCHAR(255) NOT NULL,
    latitude      DECIMAL(9, 6) NOT NULL,
    longitude     DECIMAL(9, 6) NOT NULL,

    -- total players allowed, including the organizer. The current count comes
    -- from the players who joined, so it isn't stored here
    max_players   SMALLINT UNSIGNED NOT NULL,
    skill_level   ENUM('any', 'beginner', 'intermediate', 'advanced') NOT NULL DEFAULT 'any',

    -- scheduled, cancelled or completed. "In progress" is not a status: it's a
    -- scheduled game that has started (has_started, worked out from starts_at)
    status        ENUM('scheduled', 'cancelled', 'completed') NOT NULL DEFAULT 'scheduled',

    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (game_id),
    -- organizer's games (Home screen)
    KEY idx_games_organizer (organizer_id, starts_at),
    -- upcoming games that are still on (Discover screen)
    KEY idx_games_status_starts (status, starts_at),
    CONSTRAINT fk_games_organizer FOREIGN KEY (organizer_id)
        REFERENCES users (user_id) ON DELETE CASCADE,

    CONSTRAINT chk_games_max_players CHECK (max_players BETWEEN 2 AND 100),
    CONSTRAINT chk_games_times CHECK (ends_at IS NULL OR ends_at > starts_at),
    CONSTRAINT chk_games_latitude CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT chk_games_longitude CHECK (longitude BETWEEN -180 AND 180)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
