package com.arquitechthor.kopi.application.game;

import com.arquitechthor.kopi.domain.chess.Color;
import com.arquitechthor.kopi.domain.game.BotProfile;
import java.util.Objects;

/// Start a game against `botProfile`, the person playing `humanColor`.
public record CreateGameCommand(Color humanColor, BotProfile botProfile) {

    public CreateGameCommand {
        Objects.requireNonNull(humanColor, "humanColor");
        Objects.requireNonNull(botProfile, "botProfile");
    }
}
