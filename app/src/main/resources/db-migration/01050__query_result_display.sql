-- Persisted query results for display in the AI tab and HUD overlay.
--
-- Single row per query_type ('trade_candidates' or 'outfitting').
-- Follows derive-never-remember: survives restarts, display reads from DB.
--
-- NOTE: no semicolon may appear inside these comments. Migrations are split on a semicolon at end of
-- line before comments are stripped, so one here would cut the file mid-comment and hand SQLite a
-- statement with no SQL in it.
create table if not exists query_result_display (
    query_type text primary key check (query_type in ('trade_candidates', 'outfitting')),
    saved_at text not null,
    payload_json text not null
);
