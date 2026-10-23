package com.arquitechthor.kopi.domain.game;

import com.arquitechthor.kopi.domain.chess.Move;
import java.io.Serial;
import java.util.Objects;

/// A move that is not one of the legal moves of the current position.
public final class IllegalMoveException extends GameRuleException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient Move move; // not serialized: see GameRuleException

    IllegalMoveException(GameId gameId, Move move, String fen) {
        Objects.requireNonNull(move, "move");
        this.move = move;
        super(gameId, move + " (" + move.kind() + ") is not legal in " + fen);
    }

    /// The move that was refused.
    public Move move() {
        return move;
    }
}
