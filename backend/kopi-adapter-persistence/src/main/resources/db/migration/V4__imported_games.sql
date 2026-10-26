-- Imported games (plan section 9).
--
-- An imported game has no person and no bot, arrives finished, and keeps
-- the tags of the PGN it came from. Its ending may be one the board does
-- not show (a resignation, a loss on time, an agreed draw): DECLARED, the
-- result as the PGN records it.
--
-- The tags are stored as PGN text ('[White "Morphy, Paul"]' lines), not as
-- jsonb as design section 6.1 first planned: Kopi reads PGN anyway, and
-- the persistence adapter needs no JSON library for it.

alter table game drop column pgn_headers;
alter table game add column pgn_tags text;

alter table game drop constraint game_termination_check;
alter table game add constraint game_termination_check check (termination in (
    'CHECKMATE', 'STALEMATE', 'INSUFFICIENT_MATERIAL', 'THREEFOLD_REPETITION', 'FIFTY_MOVE_RULE',
    'RESIGNATION', 'DECLARED'));

-- A played game has a person, a bot and no PGN tags; an imported one has
-- tags, no person, no bot, and is finished. A DECLARED ending is only for
-- imported games.
alter table game add constraint game_imported_consistently check (
    (origin = 'IMPORTED') = (pgn_tags is not null)
    and (origin <> 'IMPORTED' or (human_color is null and bot_profile is null and status = 'FINISHED'))
    and (termination <> 'DECLARED' or origin = 'IMPORTED')
);
