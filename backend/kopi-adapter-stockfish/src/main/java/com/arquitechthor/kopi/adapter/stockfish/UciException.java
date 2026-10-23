package com.arquitechthor.kopi.adapter.stockfish;

import java.io.Serial;

/// The engine did not behave: it could not start, it closed, it did not
/// answer in time, or it answered something unexpected. An engine that has
/// thrown this is not trusted again: the pool replaces it.
public final class UciException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    UciException(String message) {
        super(message);
    }

    UciException(String message, Throwable cause) {
        super(message, cause);
    }
}
