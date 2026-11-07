-- Online games (ADR-020): what ties a game here to its game in the relay,
-- and how far they are in step. The game itself is an ordinary row of game
-- (a LOCAL: player against a REMOTE: one, V5) with its moves.
create table online_game (
    game_id        uuid        primary key references game (id) on delete cascade,
    relay_game_id  text        not null unique,
    account_sub    text        not null,             -- the Kopi account it is played with
    desynced       boolean     not null default false,
    last_move_at   timestamptz not null,             -- the relay's time, for the 30-day rule
    draw_offer_by  text                 check (draw_offer_by in ('WHITE', 'BLACK')),
    acknowledged   boolean     not null default false -- the end was confirmed to the relay
);

create index online_game_account_idx on online_game (account_sub);
