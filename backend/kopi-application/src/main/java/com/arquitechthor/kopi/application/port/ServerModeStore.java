package com.arquitechthor.kopi.application.port;

import java.util.Objects;

/// Where Kopi Chess remembers server mode (ADR-022, decision D7: it is
/// remembered across restarts).
public interface ServerModeStore {

    /// The switch. Never empty: off by default.
    Settings settings();

    void save(Settings settings);

    /// The switch, the account and the name.
    ///
    /// @param enabled    the person turned it on
    /// @param accountSub the Kopi account it was turned on with; another
    ///                   account signed in pauses it
    /// @param name       the name the app shows for this computer; empty
    ///                   for this computer's own name
    record Settings(boolean enabled, String accountSub, String name) {

        public static final Settings OFF = new Settings(false, "", "");

        public Settings {
            accountSub = Objects.requireNonNullElse(accountSub, "");
            name = Objects.requireNonNullElse(name, "");
        }
    }
}
