-- Two people at this computer may take moves back and play them again.
-- The moves taken back and not yet played again, the next one to redo
-- first, as UCI separated by spaces; empty when there are none. They are
-- not game_move rows: game_move holds the moves of the game as it stands.
--
-- Undoing and redoing raise the version without leaving moves behind, so
-- from now on the version may be higher than replaying the moves gives.
alter table game add column undone_moves text not null default '';
