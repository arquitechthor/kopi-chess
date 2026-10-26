package com.arquitechthor.kopi.application.game;

import com.arquitechthor.kopi.application.port.AnalysisJobQueue;
import com.arquitechthor.kopi.application.port.GameRepository;
import java.time.Clock;
import java.util.Objects;

/// [ResignUseCase]: the person resigns; the bot wins, and the game's
/// analysis is queued.
public final class Resign implements ResignUseCase {

    private final GameRepository repository;
    private final AnalysisJobQueue analyses;
    private final Clock clock;

    public Resign(GameRepository repository, AnalysisJobQueue analyses, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.analyses = Objects.requireNonNull(analyses, "analyses");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public GameView resign(ResignCommand command) {
        var loaded = Games.loadAtVersion(repository, command.gameId(), command.expectedVersion());
        // An imported game has no person, but it is finished: resign refuses it as such.
        var game = loaded.resign(loaded.humanColor().orElse(null), clock.instant());
        repository.update(game, loaded.version());
        AnalysisOnFinish.request(analyses, game, clock.instant());
        return GameView.of(game);
    }
}
