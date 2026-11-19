package com.arquitechthor.kopi.application.game;

import java.io.Serial;

/// A simulated move that is not legal where it is played (or one move too
/// many): the simulation refuses it and the client keeps what it had.
public final class IllegalSimulatedMoveException extends IllegalArgumentException {

    @Serial
    private static final long serialVersionUID = 1L;

    public IllegalSimulatedMoveException(String message) {
        super(message);
    }
}
