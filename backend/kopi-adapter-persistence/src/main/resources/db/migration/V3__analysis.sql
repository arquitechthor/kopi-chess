-- Post-game analysis (design sections 6.1, 6.2 and 8).
--
-- Three tables: the analysis of a game and of each of its moves (results
-- only), and the job queue (whether an analysis is waiting, running, done
-- or failed). The opening book is not here: it lives in memory (ADR-009).
--
-- Evaluations are text, from White's point of view, in the engine's own
-- words: 'cp 34', 'mate -3', or 'checkmate WHITE' for a position that is
-- already mate (a "mate 0" has no sign to say who won).

create table analysis (
    game_id           uuid        primary key references game (id) on delete cascade,
    engine            text        not null,                                   -- 'Stockfish 19'
    limit_kind        text        not null check (limit_kind in ('DEPTH', 'NODES')),
    limit_value       bigint      not null check (limit_value >= 1),
    multi_pv          int         not null check (multi_pv >= 1),
    missed_mate_limit int         not null check (missed_mate_limit >= 1),
    first_mover       text        not null check (first_mover in ('WHITE', 'BLACK')),
    opening_eco       text                 check (opening_eco ~ '^[A-E][0-9]{2}$'),
    opening_name      text,
    started_at        timestamptz not null,
    completed_at      timestamptz not null,

    constraint analysis_opening_whole check ((opening_eco is null) = (opening_name is null)),
    constraint analysis_completed_after_started check (completed_at >= started_at)
);

create table move_analysis (
    game_id          uuid             not null references analysis (game_id) on delete cascade,
    ply              int              not null check (ply >= 1),
    eval_before      text             not null,
    eval_after       text             not null,
    best_move_uci    text             not null,
    best_line_san    text             not null,   -- space-separated: 'Rd8+ Rxd8 Rxd8#'
    classification   text             not null check (classification in ('BOOK', 'BEST', 'GOOD', 'INACCURACY',
                                                                         'MISTAKE', 'BLUNDER', 'MISSED_MATE')),
    win_chance_loss  double precision not null,
    missed_mate_in   int                       check (missed_mate_in >= 1),
    primary key (game_id, ply),
    foreign key (game_id, ply) references game_move (game_id, ply) on delete cascade,

    constraint move_analysis_evaluations check (
        eval_before ~ '^(cp -?[0-9]+|mate -?[1-9][0-9]*|checkmate (WHITE|BLACK))$'
        and eval_after ~ '^(cp -?[0-9]+|mate -?[1-9][0-9]*|checkmate (WHITE|BLACK))$'
    ),
    -- Only a missed mate says how long the mate was.
    constraint move_analysis_missed_mate check ((classification = 'MISSED_MATE') = (missed_mate_in is not null))
);

create table analysis_job (
    id          bigserial   primary key,
    game_id     uuid        not null unique references game (id) on delete cascade,
    state       text        not null check (state in ('QUEUED', 'RUNNING', 'DONE', 'FAILED')),
    attempts    int         not null default 0 check (attempts >= 0),
    last_error  text,
    locked_at   timestamptz,                 -- when a worker claimed it; only while RUNNING
    created_at  timestamptz not null,

    constraint analysis_job_locked_while_running check ((state = 'RUNNING') = (locked_at is not null))
);

-- What claimNext() looks for: the oldest queued job.
create index analysis_job_queued_idx on analysis_job (created_at) where state = 'QUEUED';
