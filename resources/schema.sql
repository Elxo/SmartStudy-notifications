CREATE TABLE IF NOT EXISTS events (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    title           TEXT    NOT NULL,
    course_code     TEXT,
    instructor      TEXT,
    location        TEXT,
    start_time      TEXT    NOT NULL,
    end_time        TEXT    NOT NULL,
    recurrence_rule TEXT,
    source          TEXT    NOT NULL DEFAULT 'manual',
    created_at      TEXT    NOT NULL DEFAULT (datetime('now')),
    updated_at      TEXT    NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS sync_log (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    event_id   INTEGER NOT NULL,
    platform   TEXT    NOT NULL,
    synced_at  TEXT    NOT NULL DEFAULT (datetime('now')),
    status     TEXT    NOT NULL,
    FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE CASCADE
);
