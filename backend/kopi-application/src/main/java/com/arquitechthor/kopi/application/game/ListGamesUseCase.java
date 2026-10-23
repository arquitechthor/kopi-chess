package com.arquitechthor.kopi.application.game;

/// The list of games, newest first, one page at a time.
public interface ListGamesUseCase {

    GamePage list(ListGamesQuery query);
}
