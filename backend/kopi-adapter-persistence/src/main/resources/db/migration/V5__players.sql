-- A player per colour (ADR-018): a bot, a person at this computer or, later,
-- a friend online. Until now a played game was always the person against a
-- bot, described by human_color and bot_profile.
--
-- Each colour's player is one text column, KIND:value:
--   'BOT:PAWN'      one of the bots
--   'LOCAL:'        the person against a bot (no name)
--   'LOCAL:Ana'     a person at this computer, with the name they gave
--   'REMOTE:luis'   a friend online, by their Kopi id
--
-- human_color and bot_profile stay, derived from the players, because the
-- history's filters and the record against each bot query them: the colour
-- of the one person here (against a bot or online) and the bot, if one
-- plays. A game between two people here has neither.
alter table game add column white_player text;
alter table game add column black_player text;

update game
   set white_player = case human_color when 'WHITE' then 'LOCAL:' else 'BOT:' || bot_profile end,
       black_player = case human_color when 'BLACK' then 'LOCAL:' else 'BOT:' || bot_profile end
 where origin = 'PLAYED';

alter table game drop constraint game_played_against_a_bot;
alter table game add constraint game_played_has_players check (
    (origin = 'PLAYED') = (white_player is not null and black_player is not null)
);
alter table game add constraint game_player_kinds check (
    white_player ~ '^(BOT:(PAWN|KNIGHT|BISHOP|ROOK|QUEEN)|LOCAL:.{0,30}|REMOTE:[A-Za-z0-9_]{3,20})$'
    and black_player ~ '^(BOT:(PAWN|KNIGHT|BISHOP|ROOK|QUEEN)|LOCAL:.{0,30}|REMOTE:[A-Za-z0-9_]{3,20})$'
);

-- Two new endings: a draw agreed by two people, and an online game left
-- for 30 days.
alter table game drop constraint game_termination_check;
alter table game add constraint game_termination_check check (termination in (
    'CHECKMATE', 'STALEMATE', 'INSUFFICIENT_MATERIAL', 'THREEFOLD_REPETITION', 'FIFTY_MOVE_RULE',
    'RESIGNATION', 'DECLARED', 'DRAW_AGREEMENT', 'INACTIVITY'));
