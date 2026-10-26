package com.arquitechthor.kopi.application.port;

import java.io.Serial;

/// The engine could not analyse a position (not installed, crashed, timed
/// out…). Nothing was judged: the caller may try again later.
public final class EngineUnavailableException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public EngineUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
