-- Docking history for piston trading and previous station navigation
--
-- Keeps the most recent 50 docking events
-- Follows derive-never-remember: survives restarts, display reads from DB
CREATE TABLE IF NOT EXISTS docking_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    stationName TEXT NOT NULL,
    starSystem TEXT NOT NULL,
    systemAddress INTEGER NOT NULL,
    marketId INTEGER NOT NULL,
    stationType TEXT,
    distFromStarLS REAL NOT NULL DEFAULT 0.0,
    dockedAt TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_docking_history_market_id ON docking_history (marketId);
