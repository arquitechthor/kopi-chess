package com.arquitechthor.kopi.domain.game;

import java.io.Serial;
import java.util.Objects;

/// An undo with no move to take back, or a redo with no move taken back
/// to play again (two people here, [Game#undo] and [Game#redo]).
public final class NothingToUndoException extends GameRuleException {

    @Serial
    private static final long serialVersionUID = 1L;

    /// What was asked for.
    public enum Direction {
        UNDO,
        REDO
    }

    private final Direction direction;

    NothingToUndoException(GameId gameId, Direction direction) {
        Objects.requireNonNull(direction, "direction");
        this.direction = direction;
        super(gameId, direction == Direction.UNDO ? "there is no move to undo" : "there is no move to redo");
    }

    /// Undo or redo.
    public Direction direction() {
        return direction;
    }
}
