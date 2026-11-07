package com.arquitechthor.kopi.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.arquitechthor.kopi.application.port.GameRepository;
import com.arquitechthor.kopi.application.port.OnlineGameStore;
import com.arquitechthor.kopi.application.port.OnlineGameStore.OnlineLink;
import com.arquitechthor.kopi.domain.chess.Color;
import com.arquitechthor.kopi.domain.chess.Position;
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
import org.springframework.jdbc.core.simple.JdbcClient;

/// The links of online games against a real PostgreSQL (V7).
@SpringBootTest(classes = JdbcGameRepositoryIT.PersistenceTestApplication.class)
class JdbcOnlineGameStoreIT {

    private static final Instant START = Instant.parse("2026-11-07T12:00:00Z");

    @Autowired
    OnlineGameStore links;

    @Autowired
    GameRepository games;

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void empty() {
        jdbc.sql("delete from game").update();
    }

    private Game onlineGame() {
        var game = Game.start(new GameId(UUID.randomUUID()), Player.Local.YOU, new Player.Remote("luis"),
                Position.initial(), START);
        games.insert(game);
        return game;
    }

    @Test
    void aLinkIsFoundByEitherSide() {
        var game = onlineGame();
        var link = new OnlineLink(game.id(), "relay-1", "sub-ana", false, START, Optional.of(Color.BLACK), false);

        links.save(link);

        assertThat(links.findByGame(game.id())).hasValue(link);
        assertThat(links.findByRelay("relay-1")).hasValue(link);
        assertThat(links.findByRelay("relay-2")).isEmpty();
    }

    @Test
    void savingAgainUpdatesTheState() {
        var game = onlineGame();
        links.save(new OnlineLink(game.id(), "relay-1", "sub-ana", false, START, Optional.empty(), false));

        var updated = new OnlineLink(game.id(), "relay-1", "sub-ana", true, START.plusSeconds(60), Optional.empty(),
                true);
        links.save(updated);

        assertThat(links.findByGame(game.id())).hasValue(updated);
    }

    @Test
    void inProgressOnlyListsUnfinishedGamesOfThatAccount() {
        var playing = onlineGame();
        var finished = onlineGame();
        var otherAccount = onlineGame();
        links.save(new OnlineLink(playing.id(), "relay-1", "sub-ana", false, START, Optional.empty(), false));
        links.save(new OnlineLink(finished.id(), "relay-2", "sub-ana", false, START, Optional.empty(), true));
        links.save(new OnlineLink(otherAccount.id(), "relay-3", "sub-other", false, START, Optional.empty(), false));
        games.update(finished.resign(Color.WHITE, START), finished.version());

        assertThat(links.inProgress("sub-ana")).extracting(OnlineLink::relayGameId).containsExactly("relay-1");
    }

    @Test
    void deletingTheGameDeletesItsLink() {
        var game = onlineGame();
        links.save(new OnlineLink(game.id(), "relay-1", "sub-ana", false, START, Optional.empty(), false));

        games.delete(game.id());

        assertThat(links.findByRelay("relay-1")).isEmpty();
    }
}
