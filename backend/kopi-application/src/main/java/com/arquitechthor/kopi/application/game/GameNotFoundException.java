package com.arquitechthor.kopi.application.game;

import com.arquitechthor.kopi.domain.game.GameId;
import java.io.Serial;
import java.util.Objects;

/// No game has this id (REST: `404`).
public final class GameNotFoundException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient GameId gameId;

    public GameNotFoundException(GameId gameId) {
        Objects.requireNonNull(gameId, "gameId");
        this.gameId = gameId;
        super("No game " + gameId);
    }

    public GameId gameId() {
        return gameId;
    }
}
