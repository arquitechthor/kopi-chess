package com.arquitechthor.kopi.application.game;

import com.arquitechthor.kopi.application.port.AnalysisJobQueue;
import com.arquitechthor.kopi.application.port.GameRepository;
import com.arquitechthor.kopi.domain.game.GameMode;
import java.time.Clock;
import java.util.Objects;

/// [AgreeDrawUseCase]: both people here agree, so the game is drawn at once
/// and its analysis is queued. Online, a draw is offered and accepted
/// through the relay instead (plan of player versus player, phase D).
public final class AgreeDraw implements AgreeDrawUseCase {

    private final GameRepository repository;
    private final AnalysisJobQueue analyses;
    private final Clock clock;

    public AgreeDraw(GameRepository repository, AnalysisJobQueue analyses, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.analyses = Objects.requireNonNull(analyses, "analyses");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public GameView agree(AgreeDrawCommand command) {
        var loaded = Games.loadAtVersion(repository, command.gameId(), command.expectedVersion());
        if (loaded.mode() != GameMode.LOCAL) {
            throw new NotALocalGameException(loaded.id(), loaded.mode());
        }
        var game = loaded.agreeDraw(clock.instant());
        repository.update(game, loaded.version());
        AnalysisOnFinish.request(analyses, game, clock.instant());
        return GameView.of(game);
    }
}
