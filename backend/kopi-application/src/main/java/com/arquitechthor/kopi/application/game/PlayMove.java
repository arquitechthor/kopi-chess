package com.arquitechthor.kopi.application.game;

import com.arquitechthor.kopi.application.port.BotPlayer;
import com.arquitechthor.kopi.application.port.GameRepository;
import java.time.Clock;
import java.util.Objects;

/// [PlayMoveUseCase]: load, check the version, play the person's move,
/// let the bot reply if the game goes on, save once.
///
/// Saving once at the end means a failure anywhere (an illegal move, the
/// bot being unavailable, someone else saving first) leaves the stored game
/// exactly as it was.
public final class PlayMove implements PlayMoveUseCase {

    private final GameRepository repository;
    private final BotPlayer bot;
    private final Clock clock;

    public PlayMove(GameRepository repository, BotPlayer bot, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.bot = Objects.requireNonNull(bot, "bot");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public GameView play(PlayMoveCommand command) {
        var loaded = Games.loadAtVersion(repository, command.gameId(), command.expectedVersion());
        var game = loaded.play(command.uci(), loaded.humanColor(), clock.instant());
        game = BotTurn.playIfBotToMove(game, bot, clock);
        repository.update(game, loaded.version());
        return GameView.of(game);
    }
}
