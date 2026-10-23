-- Games and their moves (design section 6.1).
--
-- A game row holds what cannot be derived; the current position, the SAN
-- and the outcome are recomputed by replaying the moves when the game is
-- loaded, so the domain checks them again. fen_after and san are stored
-- anyway, for queries and for the analysis of phase 5.

create table game (
    id           uuid        primary key,
    origin       text        not null check (origin in ('PLAYED', 'IMPORTED')),
    status       text        not null check (status in ('IN_PROGRESS', 'FINISHED')),
    human_color  text                 check (human_color in ('WHITE', 'BLACK')),   -- null if IMPORTED
    bot_profile  text                 check (bot_profile in ('PAWN', 'KNIGHT', 'BISHOP', 'ROOK', 'QUEEN')),
    initial_fen  text        not null,
    result       text                 check (result in ('WHITE_WIN', 'BLACK_WIN', 'DRAW')),
    termination  text                 check (termination in ('CHECKMATE', 'STALEMATE', 'INSUFFICIENT_MATERIAL',
                                                              'THREEFOLD_REPETITION', 'FIFTY_MOVE_RULE',
                                                              'RESIGNATION')),
    opening_eco  text,                                                              -- phase 5
    opening_name text,                                                              -- phase 5
    pgn_headers  jsonb,                                                             -- phase 6, imported games only
    version      bigint      not null check (version >= 0),
    created_at   timestamptz not null,
    finished_at  timestamptz,

    -- A finished game has a result, a termination and an end time; a game
    -- in progress has none of them.
    constraint game_finished_consistently check (
        (status = 'FINISHED') = (result is not null)
        and (result is null) = (termination is null)
        and (result is null) = (finished_at is null)
    ),
    constraint game_played_against_a_bot check (
        origin <> 'PLAYED' or (human_color is not null and bot_profile is not null)
    )
);

create table game_move (
    game_id    uuid        not null references game (id) on delete cascade,
    ply        int         not null check (ply >= 1),   -- 1 = White's first move
    uci        text        not null,
    san        text        not null,
    fen_after  text        not null,
    played_at  timestamptz,                            -- null for imported games
    primary key (game_id, ply)
);

create index game_created_at_idx on game (created_at desc);
