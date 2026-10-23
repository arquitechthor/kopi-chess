package com.arquitechthor.kopi.domain.game;

import java.io.Serial;
import java.util.Objects;

/// Something a game does not allow. A sealed family: these three are the
/// only ways to break the rules of the [Game] aggregate, so adapters can
/// `switch` over them exhaustively (REST: `409` for a finished game or the
/// wrong turn, `422` for an illegal move).
///
/// Like every domain error, the type is the code; the message is for logs
/// and developers, never shown to people (ADR-011).
public abstract sealed class GameRuleException extends RuntimeException
        permits GameNotInProgressException, NotYourTurnException, IllegalMoveException {

    @Serial
    private static final long serialVersionUID = 1L;

    // Exceptions are Serializable, domain values are not; these exceptions
    // never cross a JVM boundary, so the fields are simply not serialized.
    private final transient GameId gameId;

    GameRuleException(GameId gameId, String message) {
        Objects.requireNonNull(gameId, "gameId");
        this.gameId = gameId;
        super("Game " + gameId + ": " + message);
    }

    /// The game whose rules were broken.
    public GameId gameId() {
        return gameId;
    }
}
