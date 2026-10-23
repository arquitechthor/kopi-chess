package com.arquitechthor.kopi.application.game;

import com.arquitechthor.kopi.domain.game.GameId;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/// Play the move written `uci` (`e2e4`, `e7e8q`) in a game the client saw
/// at `expectedVersion`.
///
/// A command that exists is well-formed: the text is normalised to lower
/// case and must look like UCI. Whether the move is legal is the game's
/// decision.
///
/// @throws InvalidUciException if `uci` is not written like a UCI move
public record PlayMoveCommand(GameId gameId, String uci, long expectedVersion) {

    private static final Pattern UCI = Pattern.compile("[a-h][1-8][a-h][1-8][qrbn]?");

    public PlayMoveCommand {
        Objects.requireNonNull(gameId, "gameId");
        var normalised = uci == null ? "" : uci.strip().toLowerCase(Locale.ROOT);
        if (!UCI.matcher(normalised).matches()) {
            throw new InvalidUciException(String.valueOf(uci));
        }
        uci = normalised;
    }
}
