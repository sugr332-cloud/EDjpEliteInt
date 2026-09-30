-- Engineer progress tracking from Journal EngineerProgress events
--
-- Keyed by normalized name (name_key) for stable joins
-- Preserves event_timestamp to prevent older events from overwriting newer records
CREATE TABLE IF NOT EXISTS engineer_progress (
    name_key TEXT PRIMARY KEY NOT NULL,
    display_name TEXT NOT NULL,
    engineer_id INTEGER,
    progress TEXT NOT NULL,
    rank INTEGER,
    rank_progress INTEGER,
    event_timestamp TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_engineer_progress_progress ON engineer_progress (progress);
