package com.arquitechthor.kopi.adapter.rest;

import com.arquitechthor.kopi.application.game.GamePage;
import com.arquitechthor.kopi.application.game.GameSummary;
import com.arquitechthor.kopi.application.game.GameView;
import com.arquitechthor.kopi.domain.chess.Color;
import com.arquitechthor.kopi.domain.game.BotProfile;
import com.arquitechthor.kopi.domain.game.GameOutcome;
import com.arquitechthor.kopi.domain.game.GameResult;
import com.arquitechthor.kopi.domain.game.GameStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/// The JSON of the API: requests and responses as records.
///
/// The public contract of Kopi Chess (design §10), kept apart from the
/// application's views so that changing a view does not silently change
/// the API. Absent values are `null` in JSON, never missing.
final class ApiModel {

    private ApiModel() {}

    /// `POST /games`.
    record CreateGameRequest(Color humanColor, BotProfile botProfile) {}

    /// `POST /games/{id}/moves`.
    record PlayMoveRequest(String uci) {}

    /// A game: everything a client needs to show it and play on, with no
    /// chess rules on the client side (design §10.2).
    ///
    /// @param legalMoves the legal moves in UCI; empty when the game is over
    record GameResponse(
            UUID id,
            GameStatus status,
            Color humanColor,
            BotProfile botProfile,
            String fen,
            Color sideToMove,
            boolean inCheck,
            boolean humanToMove,
            List<MoveResponse> moves,
            MoveResponse lastMove,
            List<String> legalMoves,
            OutcomeResponse outcome,
            GameResult result,
            long version,
            Instant createdAt,
            Instant finishedAt) {

        static GameResponse of(GameView view) {
            var moves = view.moves().stream().map(MoveResponse::of).toList();
            return new GameResponse(view.id().value(), view.status(), view.humanColor(), view.botProfile(),
                    view.fen(), view.sideToMove(), view.inCheck(), view.isHumanToMove(), moves,
                    view.lastMove().map(MoveResponse::of).orElse(null),
                    view.legalMoves().stream().map(GameView.LegalMoveView::uci).toList(),
                    view.outcome().map(OutcomeResponse::of).orElse(null), view.result().orElse(null),
                    view.version(), view.createdAt(), view.finishedAt().orElse(null));
        }
    }

    /// A move played: `{"ply": 1, "uci": "e2e4", "san": "e4"}`.
    record MoveResponse(int ply, String uci, String san) {

        static MoveResponse of(GameView.PlayedMoveView move) {
            return new MoveResponse(move.ply(), move.uci(), move.san());
        }
    }

    /// How a game ended: `{"termination": "CHECKMATE", "winner": "BLACK"}`.
    /// `winner` is `null` for a draw.
    record OutcomeResponse(String termination, Color winner) {

        static OutcomeResponse of(GameOutcome outcome) {
            // Exhaustive over the sealed GameOutcome: a new ending needs a
            // name in the API before it compiles.
            return switch (outcome) {
                case GameOutcome.Checkmate(var winner) -> new OutcomeResponse("CHECKMATE", winner);
                case GameOutcome.Resignation(var resigned) -> new OutcomeResponse("RESIGNATION", resigned.opposite());
                case GameOutcome.Stalemate _ -> new OutcomeResponse("STALEMATE", null);
                case GameOutcome.InsufficientMaterial _ -> new OutcomeResponse("INSUFFICIENT_MATERIAL", null);
                case GameOutcome.ThreefoldRepetition _ -> new OutcomeResponse("THREEFOLD_REPETITION", null);
                case GameOutcome.FiftyMoveRule _ -> new OutcomeResponse("FIFTY_MOVE_RULE", null);
            };
        }
    }

    /// One line of the list of games.
    record GameSummaryResponse(
            UUID id,
            GameStatus status,
            Color humanColor,
            BotProfile botProfile,
            int plies,
            OutcomeResponse outcome,
            GameResult result,
            Instant createdAt,
            Instant finishedAt) {

        static GameSummaryResponse of(GameSummary summary) {
            return new GameSummaryResponse(summary.id().value(), summary.status(), summary.humanColor(),
                    summary.botProfile(), summary.plies(), summary.outcome().map(OutcomeResponse::of).orElse(null),
                    summary.result().orElse(null), summary.createdAt(), summary.finishedAt().orElse(null));
        }
    }

    /// `GET /games`: a page of games, newest first.
    record GamePageResponse(List<GameSummaryResponse> games, int page, int size, long total, int totalPages) {

        static GamePageResponse of(GamePage page) {
            return new GamePageResponse(page.games().stream().map(GameSummaryResponse::of).toList(), page.page(),
                    page.size(), page.total(), page.totalPages());
        }
    }

    /// `GET /bots`: a bot and what it is like, in the response's language.
    record BotResponse(BotProfile profile, int level, String description) {}
}
