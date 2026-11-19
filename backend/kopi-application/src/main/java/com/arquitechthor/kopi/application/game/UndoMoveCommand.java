package com.arquitechthor.kopi.application.game;

import com.arquitechthor.kopi.domain.game.GameId;
import java.util.Objects;

/// The two people at this computer take back the last move, or play again
/// the one taken back last, in a game they saw at `expectedVersion`.
public record UndoMoveCommand(GameId gameId, long expectedVersion) {

    public UndoMoveCommand {
        Objects.requireNonNull(gameId, "gameId");
    }
}
