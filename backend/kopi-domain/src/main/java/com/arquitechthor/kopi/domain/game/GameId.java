package com.arquitechthor.kopi.domain.game;

import java.util.Objects;
import java.util.UUID;

/// The identity of a game. A record around a `UUID`, so a game id cannot be
/// mixed up with any other id or string by accident.
///
/// The domain never creates ids itself: they come from the application's
/// `IdGenerator` port, which keeps tests deterministic.
///
/// @param value the UUID
public record GameId(UUID value) {

    public GameId {
        Objects.requireNonNull(value, "value");
    }

    /// The id written as text, as in URLs: `3f2c…`.
    ///
    /// @throws IllegalArgumentException if the text is not a UUID
    public static GameId parse(String text) {
        return new GameId(UUID.fromString(text));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
