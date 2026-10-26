package com.arquitechthor.kopi.application.analysis;

import com.arquitechthor.kopi.application.port.AnalysisJob;
import com.arquitechthor.kopi.domain.analysis.GameAnalysis;
import com.arquitechthor.kopi.domain.game.GameId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/// Everything about a game's analysis that a screen or an API shows: how
/// the job is going and, once done, the result.
///
/// @param gameId    the game
/// @param job       its analysis job; empty if the analysis was never asked
///                  for
/// @param analysis   the result, once saved
/// @param initialFen the game's starting position
/// @param moves      the game's moves, in order, to show next to each
///                   verdict and to step through on a board
public record AnalysisView(GameId gameId, Optional<AnalysisJob> job, Optional<GameAnalysis> analysis,
                           String initialFen, List<PlayedMove> moves) {

    public AnalysisView {
        Objects.requireNonNull(gameId, "gameId");
        Objects.requireNonNull(job, "job");
        Objects.requireNonNull(analysis, "analysis");
        Objects.requireNonNull(initialFen, "initialFen");
        moves = List.copyOf(moves);
    }

    /// A move of the game, with the position it led to.
    ///
    /// @param ply      1 for the first move
    /// @param uci      `e2e4`
    /// @param san      `e4`
    /// @param fenAfter the position after it
    public record PlayedMove(int ply, String uci, String san, String fenAfter) {}

    /// Whether the analysis is saved and can be shown.
    public boolean isDone() {
        return analysis.isPresent();
    }
}
