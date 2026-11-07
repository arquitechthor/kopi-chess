package com.arquitechthor.kopi.application.port;

import com.arquitechthor.kopi.domain.game.BotProfile;
import com.arquitechthor.kopi.domain.game.Game;
import com.arquitechthor.kopi.domain.game.GameId;
import com.arquitechthor.kopi.domain.game.GameMode;
import com.arquitechthor.kopi.domain.game.GameStatus;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// Where games are kept. Implemented by the persistence adapter.
///
/// Saving uses **optimistic locking**: [#update] says which version was
/// loaded, and only succeeds if that is still the stored one. A use case may
/// change a game more than once before saving (the human's move and the
/// bot's reply), so the stored version cannot be derived from the new one.
public interface GameRepository {

    /// The game with this id, if there is one.
    Optional<Game> findById(GameId id);

    /// Stores a new game.
    void insert(Game game);

    /// Stores `game`, which was loaded at `loadedVersion`.
    ///
    /// @throws ConcurrentGameUpdateException if the stored game is no longer
    ///                                       at `loadedVersion`
    void update(Game game, long loadedVersion);

    /// Deletes the game and everything that is its: moves, analysis and
    /// analysis job.
    ///
    /// @return whether there was such a game
    boolean delete(GameId id);

    /// The games that pass `filter`, from the newest to the oldest, skipping
    /// `offset` and returning at most `limit`.
    List<Game> findRecent(GameFilter filter, int offset, int limit);

    /// How many games pass `filter`.
    long count(GameFilter filter);

    /// The person's record against each of the five bots, as
    /// [BotRecord#tally] defines it.
    Map<BotProfile, BotRecord> recordsAgainstBots();

    /// The id and version of every game, for the history in the Kopi
    /// account (ADR-021): cheap to read, so a sync only loads the games that
    /// changed. `syncable` is false for an online game still in progress,
    /// which lives in the relay until it ends.
    List<GameVersion> versions();

    /// A game's id and version.
    record GameVersion(GameId id, long version, boolean syncable) {

        public static GameVersion of(Game game) {
            var onlineInProgress = game.mode() == GameMode.ONLINE && game.status() == GameStatus.IN_PROGRESS;
            return new GameVersion(game.id(), game.version(), !onlineInProgress);
        }
    }

    /// Every game, from the newest to the oldest, skipping `offset` and
    /// returning at most `limit`.
    default List<Game> findRecent(int offset, int limit) {
        return findRecent(GameFilter.ALL, offset, limit);
    }

    /// How many games are stored.
    default long count() {
        return count(GameFilter.ALL);
    }
}
