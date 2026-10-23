package com.arquitechthor.kopi.application.game;

import com.arquitechthor.kopi.domain.game.GameId;
import java.io.Serial;
import java.util.Objects;

/// The request was made against another version of the game than the
/// stored one: a repeated or stale request (REST: `412` for `If-Match`).
/// Nothing changed.
public final class VersionMismatchException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient GameId gameId;
    private final long expected;
    private final long actual;

    public VersionMismatchException(GameId gameId, long expected, long actual) {
        Objects.requireNonNull(gameId, "gameId");
        this.gameId = gameId;
        this.expected = expected;
        this.actual = actual;
        super("Game " + gameId + " is at version " + actual + ", not " + expected);
    }

    public GameId gameId() {
        return gameId;
    }

    /// The version the request was made against.
    public long expected() {
        return expected;
    }

    /// The version the game is at.
    public long actual() {
        return actual;
    }
}
