package com.arquitechthor.kopi.application.game;

/// Start a game against a bot. If the bot has White, it plays its first
/// move before this returns.
public interface CreateGameUseCase {

    GameView create(CreateGameCommand command);
}
