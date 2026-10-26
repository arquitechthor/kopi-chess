package com.arquitechthor.kopi.application.analysis;

import com.arquitechthor.kopi.domain.game.GameId;
import java.io.Serial;
import java.util.Objects;

/// This game cannot be analysed (yet). A code, not text (ADR-011): the
/// adapters say why in the visitor's language.
public final class AnalysisNotPossibleException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /// Why not.
    public enum Reason {
        /// The game is still being played: there is no final position yet.
        NOT_FINISHED,
        /// The game ended before any move (a resignation on move one).
        NO_MOVES
    }

    private final transient GameId gameId;
    private final Reason reason;

    public AnalysisNotPossibleException(GameId gameId, Reason reason) {
        Objects.requireNonNull(gameId, "gameId");
        Objects.requireNonNull(reason, "reason");
        this.gameId = gameId;
        this.reason = reason;
        super("Game " + gameId + " cannot be analysed: " + reason);
    }

    public GameId gameId() {
        return gameId;
    }

    public Reason reason() {
        return reason;
    }
}
