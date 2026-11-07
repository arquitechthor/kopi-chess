package com.arquitechthor.kopi.application.port;

import java.io.Serial;
import java.util.Objects;

/// Something the backend Kopi refused or could not do: the Kopi account
/// (ADR-019) or the relay of online games (ADR-020).
///
/// [#problem] is a code, not text (ADR-011): the adapters translate it. Most
/// codes are the relay's own (`docs/protocolo-rele.md`, §2); a few are about
/// this side of the connection.
public final class KopiOnlineException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /// What went wrong.
    public enum Problem {
        /// There is no Kopi session on this computer: sign in first.
        NOT_SIGNED_IN,
        /// The sign-in did not complete (cancelled, expired or tampered with).
        LOGIN_FAILED,
        /// The Kopi account still waits for an administrator's approval.
        PENDING_APPROVAL,
        /// The account needs a Kopi id before playing online.
        NO_KOPI_ID,
        KOPI_ID_TAKEN,
        ALREADY_HAS_KOPI_ID,
        INVALID_KOPI_ID,
        RESERVED_KOPI_ID,
        KOPI_ID_NOT_FOUND,
        CANNOT_INVITE_YOURSELF,
        INVALID_FEN,
        GAME_NOT_FOUND,
        NOT_ALLOWED,
        WRONG_STATUS,
        NOT_YOUR_TURN,
        /// Another move was accepted first: sync and look again.
        WRONG_PLY,
        NO_DRAW_OFFER,
        TOO_MANY_MOVES,
        /// The relay speaks another version of the protocol: update Kopi Chess.
        PROTOCOL_MISMATCH,
        TOO_MANY_REQUESTS,
        /// No connection to Kopi (no network, Kopi down, a timeout).
        UNREACHABLE,
        /// Anything else the relay answered.
        OTHER
    }

    private final Problem problem;

    public KopiOnlineException(Problem problem, String message) {
        this.problem = Objects.requireNonNull(problem, "problem");
        super(message);
    }

    public KopiOnlineException(Problem problem, String message, Throwable cause) {
        this.problem = Objects.requireNonNull(problem, "problem");
        super(message, cause);
    }

    public Problem problem() {
        return problem;
    }
}
