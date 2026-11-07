package com.arquitechthor.kopi.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.arquitechthor.kopi.application.port.GameRepository;
import com.arquitechthor.kopi.application.port.GameRepository.GameVersion;
import com.arquitechthor.kopi.application.port.HistorySyncStore;
import com.arquitechthor.kopi.application.port.HistorySyncStore.Settings;
import com.arquitechthor.kopi.application.port.HistorySyncStore.SyncedGame;
import com.arquitechthor.kopi.domain.chess.Color;
import com.arquitechthor.kopi.domain.chess.Position;
import com.arquitechthor.kopi.domain.game.BotProfile;
import com.arquitechthor.kopi.domain.game.Game;
import com.arquitechthor.kopi.domain.game.GameId;
import com.arquitechthor.kopi.domain.game.Player;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/// The history sync state against a real PostgreSQL (V8), and the cheap list
/// of game versions it relies on.
@SpringBootTest(classes = JdbcGameRepositoryIT.PersistenceTestApplication.class)
class JdbcHistorySyncStoreIT {

    private static final Instant NOW = Instant.parse("2026-11-07T18:00:00Z");

    @Autowired
    HistorySyncStore store;

    @Autowired
    GameRepository games;

    @BeforeEach
    void empty() {
        store.clearGames();
        store.saveSettings(Settings.OFF);
    }

    @Test
    void itIsOffAtFirstAndTheSettingsComeBack() {
        assertThat(store.settings().enabled()).isFalse();
        var settings = new Settings(true, "sub-1", "000017625401234#", Optional.of(NOW), Optional.of("UNREACHABLE"));

        store.saveSettings(settings);

        assertThat(store.settings()).isEqualTo(settings);
        store.saveSettings(settings.synced(NOW.plusSeconds(60)));
        assertThat(store.settings().lastProblem()).isEmpty();
    }

    @Test
    void syncedGamesAreSavedReplacedAndRemoved() {
        var id = new GameId(UUID.randomUUID());
        store.save(new SyncedGame(id, 1, 3));
        store.save(new SyncedGame(id, 2, 5));

        assertThat(store.find(id)).hasValue(new SyncedGame(id, 2, 5));
        assertThat(store.syncedGames()).containsExactly(new SyncedGame(id, 2, 5));

        store.remove(id);
        assertThat(store.find(id)).isEmpty();
    }

    @Test
    void theVersionsTellAnOnlineGameInProgressApart() {
        var bot = Game.start(new GameId(UUID.randomUUID()), new Player.Local(""), new Player.Bot(BotProfile.PAWN),
                Position.initial(), NOW).play("e2e4", Color.WHITE, NOW);
        var online = Game.start(new GameId(UUID.randomUUID()), new Player.Local(""), new Player.Remote("luis"),
                Position.initial(), NOW);
        games.insert(bot);
        games.insert(online);

        assertThat(games.versions()).contains(new GameVersion(bot.id(), 1, true),
                new GameVersion(online.id(), 0, false));
    }
}
