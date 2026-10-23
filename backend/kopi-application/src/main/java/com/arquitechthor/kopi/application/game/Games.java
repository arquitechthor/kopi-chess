package com.arquitechthor.kopi.application.game;

import com.arquitechthor.kopi.application.port.GameRepository;
import com.arquitechthor.kopi.domain.game.Game;
import com.arquitechthor.kopi.domain.game.GameId;

/// Loading a game for a use case: it must exist and, for a change, be at the
/// version the client saw.
final class Games {

    private Games() {}

    static Game load(GameRepository repository, GameId id) {
        return repository.findById(id).orElseThrow(() -> new GameNotFoundException(id));
    }

    static Game loadAtVersion(GameRepository repository, GameId id, long expectedVersion) {
        var game = load(repository, id);
        if (game.version() != expectedVersion) {
            throw new VersionMismatchException(id, expectedVersion, game.version());
        }
        return game;
    }
}
