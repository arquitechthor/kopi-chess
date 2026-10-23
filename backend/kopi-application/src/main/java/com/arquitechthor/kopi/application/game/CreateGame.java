package com.arquitechthor.kopi.application.game;

import com.arquitechthor.kopi.application.port.BotPlayer;
import com.arquitechthor.kopi.application.port.GameRepository;
import com.arquitechthor.kopi.application.port.IdGenerator;
import com.arquitechthor.kopi.domain.chess.Position;
import com.arquitechthor.kopi.domain.game.Game;
import java.time.Clock;
import java.util.Objects;

/// [CreateGameUseCase]: a new game from the initial position.
public final class CreateGame implements CreateGameUseCase {

    private final GameRepository repository;
    private final BotPlayer bot;
    private final IdGenerator ids;
    private final Clock clock;

    public CreateGame(GameRepository repository, BotPlayer bot, IdGenerator ids, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.bot = Objects.requireNonNull(bot, "bot");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public GameView create(CreateGameCommand command) {
        var game = Game.start(ids.newGameId(), command.humanColor(), command.botProfile(), Position.initial(),
                clock.instant());
        game = BotTurn.playIfBotToMove(game, bot, clock);
        repository.insert(game);
        return GameView.of(game);
    }
}
