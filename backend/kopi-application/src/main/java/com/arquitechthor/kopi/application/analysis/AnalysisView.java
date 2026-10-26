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
/// @param analysis  the result, once saved
/// @param playedSan the game's moves in SAN, in order, to show next to each
///                  verdict
public record AnalysisView(GameId gameId, Optional<AnalysisJob> job, Optional<GameAnalysis> analysis,
                           List<String> playedSan) {

    public AnalysisView {
        Objects.requireNonNull(gameId, "gameId");
        Objects.requireNonNull(job, "job");
        Objects.requireNonNull(analysis, "analysis");
        playedSan = List.copyOf(playedSan);
    }

    /// Whether the analysis is saved and can be shown.
    public boolean isDone() {
        return analysis.isPresent();
    }
}
