package com.arquitechthor.kopi.application.game;

import com.arquitechthor.kopi.application.port.GameRepository;
import java.time.Clock;
import java.util.Objects;

/// [ResignUseCase]: the person resigns; the bot wins.
public final class Resign implements ResignUseCase {

    private final GameRepository repository;
    private final Clock clock;

    public Resign(GameRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public GameView resign(ResignCommand command) {
        var loaded = Games.loadAtVersion(repository, command.gameId(), command.expectedVersion());
        var game = loaded.resign(loaded.humanColor(), clock.instant());
        repository.update(game, loaded.version());
        return GameView.of(game);
    }
}
