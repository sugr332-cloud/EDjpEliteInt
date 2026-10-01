-- Migration 01054: Recreate query_result_display with engineers query_type and create engineer_checklist table

drop table if exists query_result_display_new;

create table query_result_display_new (
    query_type text primary key check (query_type in ('trade_candidates', 'outfitting', 'engineers')),
    saved_at text not null,
    payload_json text not null
);

insert into query_result_display_new (query_type, saved_at, payload_json)
    select query_type, saved_at, payload_json from query_result_display;

drop table query_result_display;

alter table query_result_display_new rename to query_result_display;

create table if not exists engineer_checklist (
    name_key text not null,
    item text not null check (item in ('invite', 'unlock', 'referral_task')),
    checked integer not null default 0,
    updated_at text not null,
    primary key (name_key, item)
);
