package com.arquitechthor.kopi.application.port;

import com.arquitechthor.kopi.application.history.GameRecord;
import com.arquitechthor.kopi.domain.game.GameId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/// The history kept in the Kopi account (ADR-021, `docs/protocolo-rele.md`
/// §9): one entry per game, with a revision for optimistic concurrency and
/// tombstones for deleted games. The backend only stores and orders; Kopi
/// Chess merges.
///
/// Every method may throw [KopiOnlineException].
public interface AccountHistory {

    /// The entries changed after `since` (empty for all), oldest change
    /// first, and the cursor for next time.
    Changes changes(String since);

    /// Uploads `game`, last seen at `rev` (0 if new).
    PutResult put(GameId gameId, long rev, GameRecord game);

    /// Deletes the game last seen at `rev`, leaving a tombstone.
    DeleteResult delete(GameId gameId, long rev);

    /// Deletes the whole history of the account, without tombstones.
    void deleteAll();

    /// One entry. `game` is empty for a tombstone, and for a game written
    /// by a newer Kopi Chess that this one cannot read.
    record Entry(GameId gameId, long rev, boolean deleted, Optional<GameRecord> game) {
        public Entry {
            Objects.requireNonNull(gameId, "gameId");
            Objects.requireNonNull(game, "game");
        }
    }

    /// A page of changes.
    ///
    /// @param cursor        to send as `since` next time
    /// @param more          there are more pages: ask again at once
    /// @param tombstoneDays after this long without syncing, tombstones may
    ///                      be gone: sync as the first time
    record Changes(List<Entry> entries, String cursor, boolean more, int tombstoneDays) {
        public Changes {
            entries = List.copyOf(entries);
            Objects.requireNonNull(cursor, "cursor");
        }
    }

    /// What became of an upload.
    sealed interface PutResult {
        /// Stored, now at `rev`.
        record Stored(long rev) implements PutResult {}

        /// Another computer changed it first; `current` is what the account
        /// has now (empty if it no longer has the game at all).
        record Conflict(Optional<Entry> current) implements PutResult {}
    }

    /// What became of a deletion.
    sealed interface DeleteResult {
        /// Deleted (or it was already gone).
        record Done() implements DeleteResult {}

        /// Another computer changed it after `rev`; `current` is what the
        /// account has now.
        record Conflict(Entry current) implements DeleteResult {}
    }
}
