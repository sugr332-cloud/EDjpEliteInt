-- Piston mode persistent state (PT-4)
-- Holds whether piston mode is active and the current endpoints A and B
-- Single row managed (id = 1)
CREATE TABLE IF NOT EXISTS piston_mode (
    id INTEGER PRIMARY KEY,
    active BOOLEAN NOT NULL DEFAULT FALSE,
    start_type TEXT,
    station_a_name TEXT,
    station_a_system TEXT,
    station_a_system_address INTEGER,
    station_a_market_id INTEGER,
    station_b_name TEXT,
    station_b_system TEXT,
    station_b_system_address INTEGER,
    station_b_market_id INTEGER,
    commodity TEXT,
    updated_at TEXT
);
