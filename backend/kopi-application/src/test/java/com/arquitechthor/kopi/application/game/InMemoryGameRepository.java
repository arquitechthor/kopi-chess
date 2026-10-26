package com.arquitechthor.kopi.application.game;

import com.arquitechthor.kopi.application.port.BotRecord;
import com.arquitechthor.kopi.application.port.ConcurrentGameUpdateException;
import com.arquitechthor.kopi.application.port.GameFilter;
import com.arquitechthor.kopi.application.port.GameRepository;
import com.arquitechthor.kopi.domain.game.BotProfile;
import com.arquitechthor.kopi.domain.game.Game;
import com.arquitechthor.kopi.domain.game.GameId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// A [GameRepository] in a map, with the same optimistic locking contract
/// the database adapter must honour.
public final class InMemoryGameRepository implements GameRepository {

    private final Map<GameId, Game> games = new LinkedHashMap<>();
    int writes;

    @Override
    public Optional<Game> findById(GameId id) {
        return Optional.ofNullable(games.get(id));
    }

    @Override
    public void insert(Game game) {
        if (games.putIfAbsent(game.id(), game) != null) {
            throw new IllegalStateException("Game " + game.id() + " already exists");
        }
        writes++;
    }

    @Override
    public void update(Game game, long loadedVersion) {
        var stored = games.get(game.id());
        if (stored == null || stored.version() != loadedVersion) {
            throw new ConcurrentGameUpdateException(game.id(), loadedVersion);
        }
        games.put(game.id(), game);
        writes++;
    }

    @Override
    public List<Game> findRecent(GameFilter filter, int offset, int limit) {
        var newestFirst = new ArrayList<>(games.values().stream().filter(filter::matches).toList());
        newestFirst.sort(Comparator.comparing(Game::createdAt).reversed());
        return newestFirst.stream().skip(offset).limit(limit).toList();
    }

    @Override
    public long count(GameFilter filter) {
        return games.values().stream().filter(filter::matches).count();
    }

    @Override
    public Map<BotProfile, BotRecord> recordsAgainstBots() {
        return BotRecord.tally(games.values().stream());
    }

    /// Stores `game` behind the use case's back, as another request would.
    void storeDirectly(Game game) {
        games.put(game.id(), game);
    }
}
