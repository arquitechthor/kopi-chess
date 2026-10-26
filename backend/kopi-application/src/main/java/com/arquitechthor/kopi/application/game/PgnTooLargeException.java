package com.arquitechthor.kopi.application.game;

import java.io.Serial;

/// A PGN text over the import limit (design §12): one game is a few
/// kilobytes, so anything far larger is a mistake or an abuse, and is not
/// read at all (REST: `413`).
public final class PgnTooLargeException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final int limitBytes;

    public PgnTooLargeException(int limitBytes) {
        this.limitBytes = limitBytes;
        super("A PGN may have at most " + limitBytes + " bytes");
    }

    /// The limit, in bytes of UTF-8.
    public int limitBytes() {
        return limitBytes;
    }
}
