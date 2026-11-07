package com.arquitechthor.kopi.application.game;

import com.arquitechthor.kopi.domain.game.GameId;
import java.util.Objects;

/// The two people at this computer agree to a draw in a game they saw at
/// `expectedVersion`.
public record AgreeDrawCommand(GameId gameId, long expectedVersion) {

    public AgreeDrawCommand {
        Objects.requireNonNull(gameId, "gameId");
    }
}
