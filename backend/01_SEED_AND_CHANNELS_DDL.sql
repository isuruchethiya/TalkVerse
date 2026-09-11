-- =============================================================
-- TalkVerse Phase 4 — Seed + Channels DDL
-- Run BEFORE first Hibernate bootstrap because:
--   a) user_status & chat_status tables exist but are EMPTY
--   b) 5 new "channel_*" tables don't exist yet (hbm2ddl=validate
--      will FAIL if any mapped table is missing)
--
-- chat_status SEMANTICS (documented per user decision):
--   Table was empty. Using NATURAL 3-STATE PROGRESSION id=1→2→3:
--     1 = Sent       (sender app → DB confirmed, recipient not fetched yet)
--     2 = Delivered  (recipient LoadHomeData scanned it, not opened chat)
--     3 = Seen       (recipient LoadChat loaded it → green check in UI)
--   NOTE: Frontend currently treats status===1 as "green (Seen)".
--         When implementing servlets, we'll update frontend checks OR
--         flip the last-mile comparison. Do NOT reorder ids here.
--
-- user_status:
--     1 = Active     (default, can sign in)
--     2 = Suspended  (cannot sign in)
-- =============================================================

USE talk_verse;

-- =============================================================
-- 1. Seed existing EMPTY lookup tables
-- =============================================================
INSERT IGNORE INTO user_status (id, name) VALUES
    (1, 'Active'),
    (2, 'Suspended');

INSERT IGNORE INTO chat_status (id, name) VALUES
    (1, 'Sent'),
    (2, 'Delivered'),
    (3, 'Seen');

-- =============================================================
-- 2. New Channels tables (5)
-- =============================================================

CREATE TABLE IF NOT EXISTS channel_status (
    id              INT PRIMARY KEY AUTO_INCREMENT,
    name            VARCHAR(20) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO channel_status (id, name) VALUES
    (1, 'Active'),
    (2, 'Archived');

CREATE TABLE IF NOT EXISTS channel_message_status (
    id              INT PRIMARY KEY AUTO_INCREMENT,
    name            VARCHAR(20) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO channel_message_status (id, name) VALUES
    (1, 'Sent'),
    (2, 'Deleted');

CREATE TABLE IF NOT EXISTS channel (
    id                  INT PRIMARY KEY AUTO_INCREMENT,
    name                VARCHAR(45) NOT NULL,
    description         VARCHAR(255) NULL,
    created_by_user_id  INT NOT NULL,
    created_date_time   DATETIME NOT NULL,
    channel_status_id   INT NOT NULL DEFAULT 1,
    CONSTRAINT fk_channel_creator   FOREIGN KEY (created_by_user_id) REFERENCES user(id) ON DELETE CASCADE,
    CONSTRAINT fk_channel_status    FOREIGN KEY (channel_status_id)   REFERENCES channel_status(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_channel_status   ON channel(channel_status_id);
CREATE INDEX idx_channel_created  ON channel(created_date_time);

CREATE TABLE IF NOT EXISTS channel_member (
    id                  INT PRIMARY KEY AUTO_INCREMENT,
    channel_id          INT NOT NULL,
    user_id             INT NOT NULL,
    joined_date_time    DATETIME NOT NULL,
    is_admin            TINYINT(1) NOT NULL DEFAULT 0,
    CONSTRAINT fk_member_channel FOREIGN KEY (channel_id) REFERENCES channel(id) ON DELETE CASCADE,
    CONSTRAINT fk_member_user    FOREIGN KEY (user_id)    REFERENCES user(id)    ON DELETE CASCADE,
    CONSTRAINT uk_channel_user   UNIQUE (channel_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_member_user    ON channel_member(user_id);
CREATE INDEX idx_member_channel ON channel_member(channel_id);

CREATE TABLE IF NOT EXISTS channel_message (
    id                          INT PRIMARY KEY AUTO_INCREMENT,
    channel_id                  INT NOT NULL,
    from_user_id                INT NOT NULL,
    message                     TEXT NOT NULL,
    date_time                   DATETIME NOT NULL,
    channel_message_status_id   INT NOT NULL DEFAULT 1,
    CONSTRAINT fk_cmsg_channel   FOREIGN KEY (channel_id)                REFERENCES channel(id)             ON DELETE CASCADE,
    CONSTRAINT fk_cmsg_sender    FOREIGN KEY (from_user_id)              REFERENCES user(id)                ON DELETE CASCADE,
    CONSTRAINT fk_cmsg_status    FOREIGN KEY (channel_message_status_id) REFERENCES channel_message_status(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_channel_msg_channel   ON channel_message(channel_id);
CREATE INDEX idx_channel_msg_from      ON channel_message(from_user_id);
CREATE INDEX idx_channel_msg_datetime  ON channel_message(date_time);
