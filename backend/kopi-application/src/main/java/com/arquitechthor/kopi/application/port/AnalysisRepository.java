package com.arquitechthor.kopi.application.port;

import com.arquitechthor.kopi.domain.analysis.GameAnalysis;
import com.arquitechthor.kopi.domain.game.GameId;
import java.util.Optional;

/// Where finished analyses are kept. Implemented by the persistence adapter.
///
/// Only results live here; whether an analysis is waiting, running or
/// failed is the [AnalysisJobQueue]'s business.
public interface AnalysisRepository {

    /// The analysis of this game, if one has finished.
    Optional<GameAnalysis> findByGameId(GameId id);

    /// Stores `analysis`, replacing any earlier one of the same game in one
    /// go. Saving the same analysis twice is harmless: a job retried after
    /// it had already saved (design §6.2) does no damage.
    void save(GameAnalysis analysis);
}
