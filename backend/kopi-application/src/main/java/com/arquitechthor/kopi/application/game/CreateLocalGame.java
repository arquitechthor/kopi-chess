package com.arquitechthor.kopi.application.game;

import com.arquitechthor.kopi.application.port.GameRepository;
import com.arquitechthor.kopi.application.port.IdGenerator;
import com.arquitechthor.kopi.domain.chess.Position;
import com.arquitechthor.kopi.domain.game.Game;
import com.arquitechthor.kopi.domain.game.Player;
import java.time.Clock;
import java.util.Objects;

/// [CreateLocalGameUseCase]: a new game between two named people, from the
/// initial position. No bot, so nothing is played before it is saved.
public final class CreateLocalGame implements CreateLocalGameUseCase {

    private final GameRepository repository;
    private final IdGenerator ids;
    private final Clock clock;

    public CreateLocalGame(GameRepository repository, IdGenerator ids, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public GameView create(CreateLocalGameCommand command) {
        var game = Game.start(ids.newGameId(), new Player.Local(command.whiteName()),
                new Player.Local(command.blackName()), Position.initial(), clock.instant());
        repository.insert(game);
        return GameView.of(game);
    }
}
