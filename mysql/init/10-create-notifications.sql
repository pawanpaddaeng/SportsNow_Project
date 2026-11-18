-- Notifications listed on the app's Notifications screen. Runs after 03-create-games.sql
-- (name order).
--
-- Init scripts only run on an empty database. On an existing one run this file once
-- by hand (see the README). It is safe to run again: it only adds what is missing.
--
-- Push notifications are best effort, so the ones a user should still see after
-- dismissing the push are saved here too. Friend requests and chat messages aren't:
-- they have their own screens.

CREATE TABLE IF NOT EXISTS notifications (
    notification_id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    -- who it's for
    user_id         INT UNSIGNED NOT NULL,

    -- what happened, the app picks the screen a tap opens from it
    type            ENUM(
                        'friend_request_accepted',
                        'join_request',
                        'join_request_approved',
                        'join_request_declined',
                        'game_invite',
                        'game_starting'
                    ) NOT NULL,
    title           VARCHAR(100) NOT NULL,
    body            VARCHAR(255) NOT NULL,
    -- the game it's about, null for friend notifications
    game_id         INT UNSIGNED NULL,

    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- when the user saw it on the Notifications screen, null while unread
    read_at         TIMESTAMP NULL,

    PRIMARY KEY (notification_id),
    -- a user's notifications, newest first
    KEY idx_notifications_user_created (user_id, created_at),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id)
        REFERENCES users (user_id) ON DELETE CASCADE,
    -- a deleted game's notifications would open nothing, so they go with it
    CONSTRAINT fk_notifications_game FOREIGN KEY (game_id)
        REFERENCES games (game_id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
