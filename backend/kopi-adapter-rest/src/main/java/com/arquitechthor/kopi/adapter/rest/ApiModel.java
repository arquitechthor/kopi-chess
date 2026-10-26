package com.arquitechthor.kopi.adapter.rest;

import com.arquitechthor.kopi.application.analysis.AnalysisView;
import com.arquitechthor.kopi.application.game.GamePage;
import com.arquitechthor.kopi.application.game.GameSummary;
import com.arquitechthor.kopi.application.game.GameView;
import com.arquitechthor.kopi.application.port.AnalysisJob;
import com.arquitechthor.kopi.domain.analysis.AnalysisSettings;
import com.arquitechthor.kopi.domain.analysis.AnalysisSummary;
import com.arquitechthor.kopi.domain.analysis.Evaluation;
import com.arquitechthor.kopi.domain.analysis.MoveAnalysis;
import com.arquitechthor.kopi.domain.analysis.MoveCategory;
import com.arquitechthor.kopi.domain.analysis.SearchLimits;
import com.arquitechthor.kopi.domain.chess.Color;
import com.arquitechthor.kopi.domain.chess.Opening;
import com.arquitechthor.kopi.domain.game.BotProfile;
import com.arquitechthor.kopi.domain.game.GameOutcome;
import com.arquitechthor.kopi.domain.game.GameResult;
import com.arquitechthor.kopi.domain.game.GameStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    /// `GET /games/{id}/analysis`, and `POST` (which answers before any
    /// result): how the analysis is going and, once `DONE`, the result.
    /// Every result field is `null` until then.
    ///
    /// @param state    `QUEUED`, `RUNNING`, `DONE` or `FAILED`
    /// @param attempts how many times a worker has tried it
    /// @param error    why the last attempt failed, if it did
    record AnalysisResponse(
            UUID gameId,
            AnalysisJob.State state,
            int attempts,
            String error,
            String engine,
            SettingsResponse settings,
            OpeningResponse opening,
            SummaryResponse summary,
            List<MoveAnalysisResponse> moves,
            Instant startedAt,
            Instant completedAt) {

        /// The job alone, before there is a result.
        static AnalysisResponse of(AnalysisJob job) {
            return new AnalysisResponse(job.gameId().value(), job.state(), job.attempts(),
                    job.lastError().orElse(null), null, null, null, null, null, null, null);
        }

        static AnalysisResponse of(AnalysisView view) {
            if (view.analysis().isEmpty()) {
                return of(view.job().orElseThrow());
            }
            var analysis = view.analysis().get();
            var moves = new ArrayList<MoveAnalysisResponse>();
            for (var move : analysis.moves()) {
                moves.add(MoveAnalysisResponse.of(move, view.moves().get(move.ply() - 1).san()));
            }
            return new AnalysisResponse(view.gameId().value(), AnalysisJob.State.DONE,
                    view.job().map(AnalysisJob::attempts).orElse(0), null, analysis.engine(),
                    SettingsResponse.of(analysis.settings()),
                    analysis.opening().map(OpeningResponse::of).orElse(null),
                    SummaryResponse.of(analysis.summary()), moves, analysis.startedAt(), analysis.completedAt());
        }
    }

    /// How the engine searched: `{"limit": "NODES", "limitValue": 1000000,
    /// "multiPv": 2, "missedMateLimit": 5}`.
    record SettingsResponse(String limit, long limitValue, int multiPv, int missedMateLimit) {

        static SettingsResponse of(AnalysisSettings settings) {
            return switch (settings.limits()) {
                case SearchLimits.Depth(var plies) ->
                    new SettingsResponse("DEPTH", plies, settings.multiPv(), settings.missedMateLimit());
                case SearchLimits.Nodes(var count) ->
                    new SettingsResponse("NODES", count, settings.multiPv(), settings.missedMateLimit());
            };
        }
    }

    /// `{"eco": "C60", "name": "Ruy Lopez"}`. Names are in English in every
    /// language (design §12).
    record OpeningResponse(String eco, String name) {

        static OpeningResponse of(Opening opening) {
            return new OpeningResponse(opening.eco(), opening.name());
        }
    }

    /// An evaluation from White's point of view, one field set:
    /// `{"cp": 34, "mate": null, "checkmate": null}`,
    /// `{"cp": null, "mate": -3, "checkmate": null}` (Black mates in three),
    /// `{"cp": null, "mate": null, "checkmate": "WHITE"}` (the winner).
    record EvaluationResponse(Integer cp, Integer mate, Color checkmate) {

        static EvaluationResponse of(Evaluation evaluation) {
            return switch (evaluation) {
                case Evaluation.Centipawns(var cp) -> new EvaluationResponse(cp, null, null);
                case Evaluation.MateIn(var moves) -> new EvaluationResponse(null, moves, null);
                case Evaluation.Checkmate(var winner) -> new EvaluationResponse(null, null, winner);
            };
        }
    }

    /// One move's verdict. `san` is the move played; `bestLine` is the
    /// engine's line in SAN, starting with `bestMove` (in UCI).
    ///
    /// @param winChanceLoss what the move gave away, 0 to 2 (design §8.3)
    /// @param missedMateIn  for a `MISSED_MATE`, the mate that was there
    record MoveAnalysisResponse(
            int ply,
            String san,
            MoveCategory category,
            EvaluationResponse before,
            EvaluationResponse after,
            String bestMove,
            List<String> bestLine,
            double winChanceLoss,
            Integer missedMateIn) {

        static MoveAnalysisResponse of(MoveAnalysis move, String san) {
            var classification = move.classification();
            return new MoveAnalysisResponse(move.ply(), san, classification.category(),
                    EvaluationResponse.of(move.before()), EvaluationResponse.of(move.after()), move.bestMove(),
                    move.bestLine(), classification.loss(),
                    classification.missedMate().isPresent() ? classification.missedMate().getAsInt() : null);
        }
    }

    /// Both sides' accuracy and counts.
    record SummaryResponse(SideResponse white, SideResponse black) {

        static SummaryResponse of(AnalysisSummary summary) {
            return new SummaryResponse(SideResponse.of(summary.white()), SideResponse.of(summary.black()));
        }
    }

    /// One side: `accuracy` from 0 to 100 (Lichess's formula, design §8.4;
    /// `null` if the side never moved) and how many moves of each category.
    record SideResponse(Double accuracy, Map<MoveCategory, Integer> counts) {

        static SideResponse of(AnalysisSummary.Side side) {
            return new SideResponse(side.accuracy().isPresent() ? side.accuracy().getAsDouble() : null,
                    side.counts());
        }
    }
}
