package com.arquitechthor.kopi.adapter.rest;

import java.io.Serial;

/// A request the API cannot read: a missing field, a malformed header…
/// (REST: `400`, code `INVALID_REQUEST`). The message is the reason, in
/// English, and goes into the problem's detail.
final class InvalidRequestException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    InvalidRequestException(String reason) {
        super(reason);
    }
}
