package com.arquitechthor.kopi.application.account;

import java.net.URI;

/// Signing in with the Kopi account, reserving the Kopi id and signing out
/// (ADR-019). Every method but [#account] may throw
/// [com.arquitechthor.kopi.application.port.KopiOnlineException].
public interface KopiAccountUseCase {

    /// How the account stands now. Never throws: a Kopi that cannot be
    /// reached is shown as such.
    AccountView account();

    /// Where to send the browser to sign in.
    URI beginLogin();

    /// Finishes the sign-in when Kopi's login page sends the browser back.
    AccountView completeLogin(String code, String state);

    /// Reserves the Kopi id, for good.
    AccountView reserveKopiId(String kopiId);

    /// Forgets the session; returns where to send the browser to end the
    /// session of Kopi's login page too.
    URI signOut();
}
