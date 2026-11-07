package com.arquitechthor.kopi.application.game;

import com.arquitechthor.kopi.application.port.GameRepository;
import com.arquitechthor.kopi.domain.chess.Color;
import com.arquitechthor.kopi.domain.game.Game;
import com.arquitechthor.kopi.domain.game.GameId;

/// Loading a game for a use case: it must exist and, for a change, be at the
/// version the client saw.
final class Games {

    private Games() {}

    static Game load(GameRepository repository, GameId id) {
        return repository.findById(id).orElseThrow(() -> new GameNotFoundException(id));
    }

    /// The colour the person at this computer moves (or resigns) with: the
    /// side to move in a game between two people here, the person's colour
    /// against a bot or online. `null` for an imported game, which is
    /// finished, so the game refuses the action as such.
    static Color localColor(Game game) {
        return switch (game.mode()) {
            case LOCAL -> game.currentPosition().sideToMove();
            case AGAINST_BOT, ONLINE -> game.humanColor().orElseThrow();
            case IMPORTED -> null;
        };
    }

    static Game loadAtVersion(GameRepository repository, GameId id, long expectedVersion) {
        var game = load(repository, id);
        if (game.version() != expectedVersion) {
            throw new VersionMismatchException(id, expectedVersion, game.version());
        }
        return game;
    }
}
