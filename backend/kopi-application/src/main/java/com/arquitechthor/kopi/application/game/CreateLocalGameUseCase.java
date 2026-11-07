package com.arquitechthor.kopi.application.game;

/// Start a game between two people taking turns at this computer.
public interface CreateLocalGameUseCase {

    GameView create(CreateLocalGameCommand command);
}
