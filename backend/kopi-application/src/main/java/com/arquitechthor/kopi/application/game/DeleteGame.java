package com.arquitechthor.kopi.application.game;

import com.arquitechthor.kopi.application.port.GameRepository;
import com.arquitechthor.kopi.domain.game.GameId;
import java.util.Objects;

/// [DeleteGameUseCase]: the repository deletes the game and all that is its.
/// No version is asked for: a game changed in the meantime is deleted all
/// the same.
public final class DeleteGame implements DeleteGameUseCase {

    private final GameRepository repository;

    public DeleteGame(GameRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public void delete(GameId gameId) {
        if (!repository.delete(gameId)) {
            throw new GameNotFoundException(gameId);
        }
    }
}
