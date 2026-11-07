package com.arquitechthor.kopi.application.online;

import com.arquitechthor.kopi.domain.game.GameId;
import java.io.Serial;

/// An online game whose moves stopped fitting the relay's (an illegal move,
/// or another position than the friend's): it cannot go on, only be
/// resigned (plan of player versus player §6). Between two honest versions
/// of Kopi Chess it should never happen; if it does, it is a bug or an
/// incompatible version.
public final class OnlineGameDesyncedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public OnlineGameDesyncedException(GameId id) {
        super("Online game " + id + " is out of step with the relay");
    }
}
