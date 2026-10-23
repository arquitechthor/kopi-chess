package com.arquitechthor.kopi.application.port;

import com.arquitechthor.kopi.domain.game.GameId;

/// Creates ids for new games. A port, not `UUID.randomUUID()` inline, so
/// tests can predict the ids.
public interface IdGenerator {

    GameId newGameId();
}
