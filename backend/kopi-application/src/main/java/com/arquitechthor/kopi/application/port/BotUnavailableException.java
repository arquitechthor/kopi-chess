package com.arquitechthor.kopi.application.port;

import com.arquitechthor.kopi.domain.game.BotProfile;
import java.io.Serial;
import java.util.Objects;

/// The bot could not choose a move (engine not running, timed out…). The
/// use case saves nothing, so the game is as it was before the request
/// (REST: `503`).
public final class BotUnavailableException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final BotProfile profile;

    public BotUnavailableException(BotProfile profile, Throwable cause) {
        Objects.requireNonNull(profile, "profile");
        this.profile = profile;
        super("Bot " + profile + " is unavailable", cause);
    }

    public BotProfile profile() {
        return profile;
    }
}
