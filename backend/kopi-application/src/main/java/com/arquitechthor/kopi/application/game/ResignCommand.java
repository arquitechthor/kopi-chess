package com.arquitechthor.kopi.application.game;

import com.arquitechthor.kopi.domain.game.GameId;
import java.util.Objects;

/// The person resigns a game they saw at `expectedVersion`.
public record ResignCommand(GameId gameId, long expectedVersion) {

    public ResignCommand {
        Objects.requireNonNull(gameId, "gameId");
    }
}
