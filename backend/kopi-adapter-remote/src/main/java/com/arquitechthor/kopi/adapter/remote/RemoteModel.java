package com.arquitechthor.kopi.adapter.remote;

import com.arquitechthor.kopi.application.game.GameView;
import com.arquitechthor.kopi.application.port.BotRecord;
import com.arquitechthor.kopi.domain.chess.Color;
import com.arquitechthor.kopi.domain.game.BotProfile;
import com.arquitechthor.kopi.domain.game.GameOutcome;
import com.arquitechthor.kopi.domain.game.GameResult;
import com.arquitechthor.kopi.domain.game.GameStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/// The JSON of the remote protocol (`docs/protocolo-remoto.md` §3): the
/// same shapes as the REST API, trimmed to what the app draws, with records
/// of their own (adapters do not know each other, ADR-001). Absent values
/// are `null`, except `moves` in the list of games, which is left out.
final class RemoteModel {

    private RemoteModel() {}

    /// A bot, described in the app's language, with the person's record.
    record BotMessage(BotProfile profile, int level, String description, RecordMessage record) {}

    /// `{"won": 3, "lost": 5, "drawn": 1, "unfinished": 2}`.
    record RecordMessage(int won, int lost, int drawn, int unfinished) {

        static RecordMessage of(BotRecord record) {
            return new RecordMessage(record.won(), record.lost(), record.drawn(), record.unfinished());
        }
    }

    /// A game against a bot.
    ///
    /// @param moves      every move, or `null` (left out) in the list of games
    /// @param legalMoves in UCI, sorted; empty once the game is over
    record GameMessage(
            UUID id,
            GameStatus status,
            BotProfile botProfile,
            Color humanColor,
            String fen,
            Color sideToMove,
            boolean inCheck,
            boolean humanToMove,
            @JsonInclude(JsonInclude.Include.NON_NULL) List<MoveMessage> moves,
            MoveMessage lastMove,
            List<String> legalMoves,
            OutcomeMessage outcome,
            GameResult result,
            long version,
            Instant createdAt) {

        /// With every move: the answer to `game`, `new`, `move` and `resign`.
        static GameMessage of(GameView view) {
            return of(view, view.moves().stream().map(MoveMessage::of).toList());
        }

        /// Without the moves: one line of `games`.
        static GameMessage withoutMoves(GameView view) {
            return of(view, null);
        }

        private static GameMessage of(GameView view, List<MoveMessage> moves) {
            return new GameMessage(view.id().value(), view.status(), view.botProfile().orElse(null),
                    view.humanColor().orElse(null), view.fen(), view.sideToMove(), view.inCheck(),
                    view.isHumanToMove(), moves, view.lastMove().map(MoveMessage::of).orElse(null),
                    view.legalMoves().stream().map(GameView.LegalMoveView::uci).toList(),
                    view.outcome().map(OutcomeMessage::of).orElse(null), view.result().orElse(null), view.version(),
                    view.createdAt());
        }
    }

    /// `{"ply": 1, "uci": "e2e4", "san": "e4"}`.
    record MoveMessage(int ply, String uci, String san) {

        static MoveMessage of(GameView.PlayedMoveView move) {
            return new MoveMessage(move.ply(), move.uci(), move.san());
        }
    }

    /// How a game ended, as in the REST API: `winner` is `null` for a draw.
    record OutcomeMessage(String termination, Color winner) {

        static OutcomeMessage of(GameOutcome outcome) {
            // Exhaustive over the sealed GameOutcome, like the REST API.
            return switch (outcome) {
                case GameOutcome.Checkmate(var winner) -> new OutcomeMessage("CHECKMATE", winner);
                case GameOutcome.Resignation(var resigned) -> new OutcomeMessage("RESIGNATION", resigned.opposite());
                case GameOutcome.Stalemate _ -> new OutcomeMessage("STALEMATE", null);
                case GameOutcome.InsufficientMaterial _ -> new OutcomeMessage("INSUFFICIENT_MATERIAL", null);
                case GameOutcome.ThreefoldRepetition _ -> new OutcomeMessage("THREEFOLD_REPETITION", null);
                case GameOutcome.FiftyMoveRule _ -> new OutcomeMessage("FIFTY_MOVE_RULE", null);
                case GameOutcome.DrawByAgreement _ -> new OutcomeMessage("DRAW_AGREEMENT", null);
                case GameOutcome.Inactivity(var winner) -> new OutcomeMessage("INACTIVITY", winner);
                case GameOutcome.Declared(var result) -> new OutcomeMessage("DECLARED", switch (result) {
                    case WHITE_WIN -> Color.WHITE;
                    case BLACK_WIN -> Color.BLACK;
                    case DRAW -> null;
                });
            };
        }
    }
}
