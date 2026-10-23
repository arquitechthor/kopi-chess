package com.arquitechthor.kopi.application.port;

import com.arquitechthor.kopi.domain.game.GameId;
import java.io.Serial;
import java.util.Objects;

/// Someone else saved the game between our load and our save (two tabs, a
/// double click). Nothing of ours was stored; the client should reload.
public final class ConcurrentGameUpdateException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient GameId gameId;

    public ConcurrentGameUpdateException(GameId gameId, long loadedVersion) {
        Objects.requireNonNull(gameId, "gameId");
        this.gameId = gameId;
        super("Game " + gameId + " changed after it was loaded at version " + loadedVersion);
    }

    public GameId gameId() {
        return gameId;
    }
}
