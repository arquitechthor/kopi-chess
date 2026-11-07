package com.arquitechthor.kopi.application.game;

import com.arquitechthor.kopi.domain.game.GameId;
import com.arquitechthor.kopi.domain.game.GameMode;
import java.io.Serial;

/// An action only two people at this computer can take (agreeing to a draw
/// on the spot), asked of another kind of game (REST: `409`).
public final class NotALocalGameException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public NotALocalGameException(GameId id, GameMode mode) {
        super("Game " + id + " is not a game between two people here but " + mode);
    }
}
