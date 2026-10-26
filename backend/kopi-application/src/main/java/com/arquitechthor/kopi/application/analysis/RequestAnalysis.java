package com.arquitechthor.kopi.application.analysis;

import com.arquitechthor.kopi.application.game.GameNotFoundException;
import com.arquitechthor.kopi.application.port.AnalysisJob;
import com.arquitechthor.kopi.application.port.AnalysisJobQueue;
import com.arquitechthor.kopi.application.port.GameRepository;
import com.arquitechthor.kopi.domain.game.GameId;
import com.arquitechthor.kopi.domain.game.GameStatus;
import java.time.Clock;
import java.util.Objects;

/// [RequestAnalysisUseCase]: check the game can be analysed, then queue it.
public final class RequestAnalysis implements RequestAnalysisUseCase {

    private final GameRepository games;
    private final AnalysisJobQueue queue;
    private final Clock clock;

    public RequestAnalysis(GameRepository games, AnalysisJobQueue queue, Clock clock) {
        this.games = Objects.requireNonNull(games, "games");
        this.queue = Objects.requireNonNull(queue, "queue");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public AnalysisJob request(GameId gameId) {
        var game = games.findById(gameId).orElseThrow(() -> new GameNotFoundException(gameId));
        if (game.status() != GameStatus.FINISHED) {
            throw new AnalysisNotPossibleException(gameId, AnalysisNotPossibleException.Reason.NOT_FINISHED);
        }
        if (game.moves().isEmpty()) {
            throw new AnalysisNotPossibleException(gameId, AnalysisNotPossibleException.Reason.NO_MOVES);
        }
        return queue.enqueue(gameId, clock.instant());
    }
}
