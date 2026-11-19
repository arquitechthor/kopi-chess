package com.arquitechthor.kopi.application.game;

import com.arquitechthor.kopi.application.port.AnalysisJobQueue;
import com.arquitechthor.kopi.application.port.GameRepository;
import com.arquitechthor.kopi.domain.game.Game;
import com.arquitechthor.kopi.domain.game.GameMode;
import java.time.Clock;
import java.util.Objects;
import java.util.function.UnaryOperator;

/// [UndoMoveUseCase]: load, check the version and that it is a game between
/// two people here, take the move back (or play it again), save. A move
/// played again may end the game, which then queues its analysis.
public final class UndoMove implements UndoMoveUseCase {

    private final GameRepository repository;
    private final AnalysisJobQueue analyses;
    private final Clock clock;

    public UndoMove(GameRepository repository, AnalysisJobQueue analyses, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.analyses = Objects.requireNonNull(analyses, "analyses");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public GameView undo(UndoMoveCommand command) {
        return change(command, Game::undo);
    }

    @Override
    public GameView redo(UndoMoveCommand command) {
        return change(command, loaded -> loaded.redo(clock.instant()));
    }

    private GameView change(UndoMoveCommand command, UnaryOperator<Game> action) {
        var loaded = Games.loadAtVersion(repository, command.gameId(), command.expectedVersion());
        if (loaded.mode() != GameMode.LOCAL) {
            throw new NotALocalGameException(loaded.id(), loaded.mode());
        }
        var game = action.apply(loaded);
        repository.update(game, loaded.version());
        AnalysisOnFinish.request(analyses, game, clock.instant());
        return GameView.of(game);
    }
}
