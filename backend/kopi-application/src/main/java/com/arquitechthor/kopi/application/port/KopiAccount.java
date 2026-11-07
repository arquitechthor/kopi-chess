package com.arquitechthor.kopi.application.port;

import java.net.URI;
import java.util.Optional;

/// The person's Kopi account, from Kopi Chess (ADR-019): signing in on
/// Kopi's own login page and the public Kopi id. The adapter talks to
/// Cognito and to the backend Kopi; the password never goes through Kopi
/// Chess.
///
/// Every method may throw [KopiOnlineException].
public interface KopiAccount {

    /// Where to send the browser to sign in (Authorization Code with PKCE).
    /// The adapter remembers what it needs to finish in [#completeLogin].
    URI beginLogin();

    /// Finishes the sign-in with what Kopi's login page sent back, and
    /// keeps the session on this computer.
    ///
    /// @throws KopiOnlineException `LOGIN_FAILED` if `state` is unknown or
    ///                             the code is not accepted
    void completeLogin(String code, String state);

    /// The session on this computer, if the person signed in.
    Optional<Session> session();

    /// The account's Kopi id, if it has one.
    ///
    /// @throws KopiOnlineException `NOT_SIGNED_IN`, `PENDING_APPROVAL`,
    ///                             `UNREACHABLE`…
    Optional<String> kopiId();

    /// Reserves `kopiId` for the account, for good: it cannot be changed.
    ///
    /// @return the id as stored (as written the first time)
    /// @throws KopiOnlineException `KOPI_ID_TAKEN`, `ALREADY_HAS_KOPI_ID`,
    ///                             `INVALID_KOPI_ID`, `RESERVED_KOPI_ID`…
    String reserveKopiId(String kopiId);

    /// Whether `kopiId` belongs to someone, and how they wrote it.
    Optional<String> findKopiId(String kopiId);

    /// Forgets the session on this computer (revoking it at Kopi when
    /// possible) and returns where to send the browser to end the session
    /// of Kopi's login page too.
    URI signOut();

    /// Who is signed in: the account's `sub` and its email (shown only to
    /// the person themself, never to other players).
    record Session(String sub, String email) {}
}
