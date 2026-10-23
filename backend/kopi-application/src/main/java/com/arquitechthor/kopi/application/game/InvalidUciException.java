package com.arquitechthor.kopi.application.game;

import java.io.Serial;

/// Text that is not written like a UCI move at all (`hello`, `e9e4`), as
/// opposed to a well-formed move that is not legal (REST: `400` vs `422`).
public final class InvalidUciException extends IllegalArgumentException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String text;

    public InvalidUciException(String text) {
        this.text = text;
        super("Not a UCI move: \"" + text + "\"");
    }

    /// The text as it was given.
    public String text() {
        return text;
    }
}
