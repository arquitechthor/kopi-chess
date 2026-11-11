package com.arquitechthor.kopi.application.servermode;

import java.util.Objects;

/// Server mode as the pages show it.
///
/// @param enabled      the person turned it on
/// @param signedIn     there is a Kopi session on this computer
/// @param otherAccount it was turned on with another Kopi account than the
///                     one signed in now: paused
/// @param name         the name the app shows for this computer
public record ServerModeView(boolean enabled, boolean signedIn, boolean otherAccount, String name) {

    public ServerModeView {
        Objects.requireNonNull(name, "name");
    }

    /// Whether the phone can reach this computer now (given the relay can
    /// be reached): on, signed in, and with the same account. Every page
    /// warns while it is.
    public boolean active() {
        return enabled && signedIn && !otherAccount;
    }
}
