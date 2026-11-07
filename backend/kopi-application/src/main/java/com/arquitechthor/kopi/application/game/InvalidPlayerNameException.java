package com.arquitechthor.kopi.application.game;

import java.io.Serial;

/// A name for a person that cannot be used: empty in a game between two
/// people here (each needs one), or longer than
/// [com.arquitechthor.kopi.domain.game.Player.Local#MAX_NAME] (REST: `400`).
public final class InvalidPlayerNameException extends IllegalArgumentException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidPlayerNameException(String message) {
        super(message);
    }
}
