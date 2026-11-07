package com.arquitechthor.kopi.application.history;

import java.time.Instant;
import java.util.Optional;

/// The history in the Kopi account as the account page shows it.
///
/// @param enabled      the person turned it on
/// @param signedIn     there is a Kopi session on this computer
/// @param otherAccount it was turned on with another Kopi account than the
///                     one signed in now: paused
/// @param lastSyncAt   when a sync last finished
/// @param lastProblem  why the last sync failed, if it did (a
///                     `KopiOnlineException.Problem` name)
/// @param syncedGames  games in step with the account
/// @param pendingGames games here with changes still to upload
public record HistoryView(boolean enabled, boolean signedIn, boolean otherAccount, Optional<Instant> lastSyncAt,
                          Optional<String> lastProblem, int syncedGames, int pendingGames) {

    /// Whether it is on and syncing with the account signed in.
    public boolean active() {
        return enabled && signedIn && !otherAccount;
    }
}
