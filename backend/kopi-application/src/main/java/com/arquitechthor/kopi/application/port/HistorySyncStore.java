package com.arquitechthor.kopi.application.port;

import com.arquitechthor.kopi.domain.game.GameId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/// Where Kopi Chess remembers how far its games are in step with the
/// history in the Kopi account (ADR-021).
public interface HistorySyncStore {

    /// The switch and the cursor. Never empty: off by default.
    Settings settings();

    void saveSettings(Settings settings);

    /// Every game known to be in the account, with the revision seen.
    List<SyncedGame> syncedGames();

    Optional<SyncedGame> find(GameId gameId);

    /// Inserts or replaces.
    void save(SyncedGame game);

    void remove(GameId gameId);

    /// Forgets every synced game (the cursor stays in [Settings]).
    void clearGames();

    /// The switch and where the last sync stopped.
    ///
    /// @param enabled     the person turned it on
    /// @param accountSub  the Kopi account it syncs with; another account
    ///                    signed in pauses it
    /// @param cursor      the account's cursor after the last page read
    /// @param lastSyncAt  when a sync last finished
    /// @param lastProblem why the last sync failed, if it did (a
    ///                    [KopiOnlineException.Problem] name)
    record Settings(boolean enabled, String accountSub, String cursor, Optional<Instant> lastSyncAt,
                    Optional<String> lastProblem) {

        public static final Settings OFF = new Settings(false, "", "", Optional.empty(), Optional.empty());

        public Settings {
            accountSub = accountSub == null ? "" : accountSub;
            cursor = cursor == null ? "" : cursor;
            Objects.requireNonNull(lastSyncAt, "lastSyncAt");
            Objects.requireNonNull(lastProblem, "lastProblem");
        }

        public Settings withCursor(String next) {
            return new Settings(enabled, accountSub, next, lastSyncAt, lastProblem);
        }

        public Settings synced(Instant at) {
            return new Settings(enabled, accountSub, cursor, Optional.of(at), Optional.empty());
        }

        public Settings failed(String problem) {
            return new Settings(enabled, accountSub, cursor, lastSyncAt, Optional.of(problem));
        }
    }

    /// A game in step with the account.
    ///
    /// @param rev           the account's revision of it, last seen
    /// @param syncedVersion the game's version here when it was last in
    ///                      step: a higher one means changes to upload
    record SyncedGame(GameId gameId, long rev, long syncedVersion) {
        public SyncedGame {
            Objects.requireNonNull(gameId, "gameId");
        }
    }
}
