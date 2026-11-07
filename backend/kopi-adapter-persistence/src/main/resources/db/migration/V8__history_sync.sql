-- The history in the Kopi account (ADR-021): the switch and the cursor
-- (one row), and how far each game is in step with the account.
create table history_sync (
    id            int         primary key default 1 check (id = 1),
    enabled       boolean     not null default false,
    account_sub   text        not null default '',   -- the Kopi account it syncs with
    cursor_value  text        not null default '',   -- the account's cursor after the last page
    last_sync_at  timestamptz,
    last_problem  text
);

-- No foreign key to game: a row whose game is gone is a deletion still to
-- upload.
create table history_game (
    game_id         uuid   primary key,
    rev             bigint not null,                 -- the account's revision, last seen
    synced_version  bigint not null                  -- the game's version when last in step
);
