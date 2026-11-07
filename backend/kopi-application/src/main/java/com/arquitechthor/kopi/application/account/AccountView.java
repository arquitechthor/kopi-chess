package com.arquitechthor.kopi.application.account;

import java.util.Optional;

/// The Kopi account as the pages show it.
///
/// @param signedIn         whether there is a Kopi session on this computer
/// @param email            the account's email, shown only to the person
/// @param kopiId           the public Kopi id, once reserved
/// @param pendingApproval  the account waits for an administrator
/// @param unreachable      Kopi could not be reached just now (the session
///                         is kept; the id is unknown until it can)
public record AccountView(boolean signedIn, Optional<String> email, Optional<String> kopiId,
                          boolean pendingApproval, boolean unreachable) {

    /// No session on this computer.
    public static final AccountView SIGNED_OUT =
            new AccountView(false, Optional.empty(), Optional.empty(), false, false);

    /// Whether the account can play online: signed in, approved and with a
    /// Kopi id.
    public boolean readyToPlay() {
        return signedIn && kopiId.isPresent() && !pendingApproval;
    }
}
