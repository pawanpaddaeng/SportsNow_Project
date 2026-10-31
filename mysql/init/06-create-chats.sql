-- Chat rooms and their messages. Runs after 03-create-games.sql (name order).
--
-- Init scripts only run on an empty database. On an existing one run this file once
-- by hand (see the README). It is safe to run again: it only adds what is missing.
--
-- A game has one chat, created with the game. Who can read it and whether it is closed
-- come from game_players and the game's status, so they aren't stored here.

CREATE TABLE IF NOT EXISTS chats (
    chat_id    INT UNSIGNED NOT NULL AUTO_INCREMENT,
    game_id    INT UNSIGNED NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (chat_id),
    UNIQUE KEY uq_chats_game (game_id),
    CONSTRAINT fk_chats_game FOREIGN KEY (game_id)
        REFERENCES games (game_id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS chat_messages (
    message_id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    chat_id    INT UNSIGNED NOT NULL,
    sender_id  INT UNSIGNED NOT NULL,
    text       VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (message_id),
    -- a chat's messages, newest first, one page at a time
    KEY idx_chat_messages_chat (chat_id, message_id),
    CONSTRAINT fk_chat_messages_chat FOREIGN KEY (chat_id)
        REFERENCES chats (chat_id) ON DELETE CASCADE,
    CONSTRAINT fk_chat_messages_sender FOREIGN KEY (sender_id)
        REFERENCES users (user_id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- games made before this script have no chat yet
INSERT INTO chats (game_id)
SELECT g.game_id FROM games g
WHERE NOT EXISTS (SELECT 1 FROM chats c WHERE c.game_id = g.game_id);
