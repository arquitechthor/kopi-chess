package com.arquitechthor.kopi.domain.game;

import java.io.Serial;
import java.util.Objects;

/// A move that is not one of the legal moves of the current position.
public final class IllegalMoveException extends GameRuleException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String uci;

    IllegalMoveException(GameId gameId, String uci, String detail) {
        Objects.requireNonNull(uci, "uci");
        this.uci = uci;
        super(gameId, uci + " " + detail);
    }

    /// The refused move in UCI, as it was given (`e2e5`).
    public String uci() {
        return uci;
    }
}
