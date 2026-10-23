package com.arquitechthor.kopi.domain.game;

import com.arquitechthor.kopi.domain.chess.Color;
import java.io.Serial;
import java.util.Objects;

/// A move by the side that is not to move.
public final class NotYourTurnException extends GameRuleException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Color color;

    NotYourTurnException(GameId gameId, Color color) {
        Objects.requireNonNull(color, "color");
        this.color = color;
        super(gameId, color + " tried to move, but it is " + color.opposite() + "'s turn");
    }

    /// The side that tried to move out of turn.
    public Color color() {
        return color;
    }
}
