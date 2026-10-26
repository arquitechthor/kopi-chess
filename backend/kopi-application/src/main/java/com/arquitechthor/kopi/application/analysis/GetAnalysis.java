package com.arquitechthor.kopi.application.analysis;

import com.arquitechthor.kopi.application.game.GameNotFoundException;
import com.arquitechthor.kopi.application.port.AnalysisJobQueue;
import com.arquitechthor.kopi.application.port.AnalysisRepository;
import com.arquitechthor.kopi.application.port.GameRepository;
import com.arquitechthor.kopi.domain.game.GameId;
import com.arquitechthor.kopi.domain.game.PlayedMove;
import java.util.Objects;

/// [GetAnalysisUseCase]: the game, its job and its result, read together.
public final class GetAnalysis implements GetAnalysisUseCase {

    private final GameRepository games;
    private final AnalysisJobQueue queue;
    private final AnalysisRepository analyses;

    public GetAnalysis(GameRepository games, AnalysisJobQueue queue, AnalysisRepository analyses) {
        this.games = Objects.requireNonNull(games, "games");
        this.queue = Objects.requireNonNull(queue, "queue");
        this.analyses = Objects.requireNonNull(analyses, "analyses");
    }

    @Override
    public AnalysisView get(GameId gameId) {
        var game = games.findById(gameId).orElseThrow(() -> new GameNotFoundException(gameId));
        return new AnalysisView(gameId, queue.findByGameId(gameId), analyses.findByGameId(gameId),
                game.moves().stream().map(PlayedMove::san).toList());
    }
}
