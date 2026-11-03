-- Friend requests and confirmed friendships.
-- Runs after 01-create-users.sql (name order).
--
-- A sender has at most one request per receiver. Asking again after being declined
-- updates that row back to pending instead of adding a new one.

CREATE TABLE IF NOT EXISTS friend_requests (
    request_id   INT UNSIGNED NOT NULL AUTO_INCREMENT,
    sender_id    INT UNSIGNED NOT NULL,
    receiver_id  INT UNSIGNED NOT NULL,

    -- pending until the receiver responds
    status       ENUM('pending', 'accepted', 'declined') NOT NULL DEFAULT 'pending',
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- when the receiver accepted or declined, null while pending
    responded_at TIMESTAMP NULL,

    PRIMARY KEY (request_id),
    UNIQUE KEY uq_friend_requests_sender_receiver (sender_id, receiver_id),
    -- a user's incoming pending requests
    KEY idx_friend_requests_receiver_status (receiver_id, status, created_at),
    CONSTRAINT fk_friend_requests_sender FOREIGN KEY (sender_id)
        REFERENCES users (user_id) ON DELETE CASCADE,
    CONSTRAINT fk_friend_requests_receiver FOREIGN KEY (receiver_id)
        REFERENCES users (user_id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- A friendship is two rows, one for each user, so a user's friends are found by user_id alone.
CREATE TABLE IF NOT EXISTS friendships (
    user_id    INT UNSIGNED NOT NULL,
    friend_id  INT UNSIGNED NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (user_id, friend_id),
    CONSTRAINT fk_friendships_user FOREIGN KEY (user_id)
        REFERENCES users (user_id) ON DELETE CASCADE,
    CONSTRAINT fk_friendships_friend FOREIGN KEY (friend_id)
        REFERENCES users (user_id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
