package com.arquitechthor.kopi.application.servermode;

/// Turning server mode on and off from the account page (ADR-022).
public interface ServerModeUseCase {

    /// How it stands now. Never throws.
    ServerModeView view();

    /// Turns it on for the account signed in, under `name` (blank: this
    /// computer's own name).
    ///
    /// @throws com.arquitechthor.kopi.application.port.KopiOnlineException
    ///         `NOT_SIGNED_IN` without a Kopi session
    ServerModeView enable(String name);

    /// Turns it off.
    ServerModeView disable();

    /// Changes the name the app shows (blank: this computer's own name).
    ServerModeView rename(String name);

    /// Connects as a server or stops, as the switch and the session stand
    /// now: at start-up, and every so often in case the person signed in,
    /// out or with another account.
    void apply();
}
