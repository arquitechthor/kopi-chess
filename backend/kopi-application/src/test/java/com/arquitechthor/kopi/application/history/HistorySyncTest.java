package com.arquitechthor.kopi.application.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.arquitechthor.kopi.application.analysis.InMemoryAnalysisJobQueue;
import com.arquitechthor.kopi.application.game.InMemoryGameRepository;
import com.arquitechthor.kopi.application.port.AccountHistory;
import com.arquitechthor.kopi.application.port.GameFilter;
import com.arquitechthor.kopi.application.port.HistorySyncStore;
import com.arquitechthor.kopi.application.port.KopiAccount;
import com.arquitechthor.kopi.application.port.KopiOnlineException;
import com.arquitechthor.kopi.application.port.KopiOnlineException.Problem;
import com.arquitechthor.kopi.domain.chess.Position;
import com.arquitechthor.kopi.domain.game.BotProfile;
import com.arquitechthor.kopi.domain.game.Game;
import com.arquitechthor.kopi.domain.game.GameId;
import com.arquitechthor.kopi.domain.game.GameStatus;
import com.arquitechthor.kopi.domain.game.Player;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/// Two computers of the same person keep their games in step through the
/// history in the Kopi account (ADR-021), with the account in memory
/// following the rules of `docs/protocolo-rele.md` §9.
class HistorySyncTest {

    static final Instant T0 = Instant.parse("2026-11-07T10:00:00Z");
    final Clock clock = Clock.fixed(T0, ZoneOffset.UTC);
    final FakeAccountHistory cloud = new FakeAccountHistory();
    final Computer a = new Computer("ana", 1);
    final Computer b = new Computer("ana", 2);

    // ── An account in memory ────────────────────────────────────────────

    static final class FakeAccountHistory implements AccountHistory {
        record Stored(long rev, boolean deleted, GameRecord game, long seq) {}

        final Map<GameId, Stored> entries = new LinkedHashMap<>();
        long seq;
        int puts;
        boolean down;

        private void check() {
            if (down) {
                throw new KopiOnlineException(Problem.UNREACHABLE, "down");
            }
        }

        @Override
        public Changes changes(String since) {
            check();
            var from = since.isEmpty() ? 0 : Long.parseLong(since);
            var list = entries.entrySet().stream().filter(e -> e.getValue().seq() > from)
                    .sorted((x, y) -> Long.compare(x.getValue().seq(), y.getValue().seq()))
                    .map(e -> new Entry(e.getKey(), e.getValue().rev(), e.getValue().deleted(),
                            Optional.ofNullable(e.getValue().game())))
                    .toList();
            return new Changes(list, String.valueOf(seq), false, 180);
        }

        @Override
        public PutResult put(GameId gameId, long rev, GameRecord game) {
            check();
            puts++;
            var current = entries.get(gameId);
            var currentRev = current == null ? 0 : current.rev();
            if (current == null ? rev != 0 : rev != currentRev) {
                return new PutResult.Conflict(Optional.ofNullable(current).map(c -> entry(gameId, c)));
            }
            entries.put(gameId, new Stored(currentRev + 1, false, game, ++seq));
            return new PutResult.Stored(currentRev + 1);
        }

        @Override
        public DeleteResult delete(GameId gameId, long rev) {
            check();
            var current = entries.get(gameId);
            if (current == null || current.deleted()) {
                return new DeleteResult.Done();
            }
            if (current.rev() != rev) {
                return new DeleteResult.Conflict(entry(gameId, current));
            }
            entries.put(gameId, new Stored(rev + 1, true, null, ++seq));
            return new DeleteResult.Done();
        }

        @Override
        public void deleteAll() {
            check();
            entries.clear();
        }

        private static Entry entry(GameId id, Stored s) {
            return new Entry(id, s.rev(), s.deleted(), Optional.ofNullable(s.game()));
        }

        long live() {
            return entries.values().stream().filter(s -> !s.deleted()).count();
        }
    }

    static final class MemoryStore implements HistorySyncStore {
        Settings settings = Settings.OFF;
        final Map<GameId, SyncedGame> games = new LinkedHashMap<>();

        @Override
        public Settings settings() {
            return settings;
        }

        @Override
        public void saveSettings(Settings settings) {
            this.settings = settings;
        }

        @Override
        public List<SyncedGame> syncedGames() {
            return List.copyOf(games.values());
        }

        @Override
        public Optional<SyncedGame> find(GameId gameId) {
            return Optional.ofNullable(games.get(gameId));
        }

        @Override
        public void save(SyncedGame game) {
            games.put(game.gameId(), game);
        }

        @Override
        public void remove(GameId gameId) {
            games.remove(gameId);
        }

        @Override
        public void clearGames() {
            games.clear();
        }
    }

    /// One computer: its games, its sync state and who is signed in.
    final class Computer {
        final InMemoryGameRepository games = new InMemoryGameRepository();
        final InMemoryAnalysisJobQueue analyses = new InMemoryAnalysisJobQueue();
        final MemoryStore store = new MemoryStore();
        final HistorySync history;
        String sub;

        Computer(String sub, long prefix) {
            this.sub = sub;
            var ids = new AtomicLong();
            KopiAccount account = new KopiAccount() {
                @Override
                public URI beginLogin() {
                    return URI.create("https://login.example");
                }

                @Override
                public void completeLogin(String code, String state) {}

                @Override
                public Optional<Session> session() {
                    return Optional.ofNullable(Computer.this.sub).map(s -> new Session(s, ""));
                }

                @Override
                public Optional<String> kopiId() {
                    return Optional.empty();
                }

                @Override
                public String reserveKopiId(String kopiId) {
                    return kopiId;
                }

                @Override
                public Optional<String> findKopiId(String kopiId) {
                    return Optional.empty();
                }

                @Override
                public URI signOut() {
                    return URI.create("https://logout.example");
                }
            };
            history = new HistorySync(cloud, store, games, analyses, account,
                    () -> new GameId(new UUID(prefix + 100, ids.incrementAndGet())), clock);
        }

        Game newBotGame(String... moves) {
            var game = Game.start(new GameId(UUID.randomUUID()), new Player.Local(""), new Player.Bot(BotProfile.PAWN),
                    Position.initial(), T0);
            for (var uci : moves) {
                game = game.play(uci, game.currentPosition().sideToMove(), T0);
            }
            games.insert(game);
            return game;
        }

        Game play(GameId id, String... moves) {
            var loaded = game(id);
            var game = loaded;
            for (var uci : moves) {
                game = game.play(uci, game.currentPosition().sideToMove(), T0);
            }
            games.update(game, loaded.version());
            return game;
        }

        Game resign(GameId id) {
            var loaded = game(id);
            var game = loaded.resign(loaded.currentPosition().sideToMove(), T0);
            games.update(game, loaded.version());
            return game;
        }

        Game game(GameId id) {
            return games.findById(id).orElseThrow();
        }

        List<Game> all() {
            return games.findRecent(GameFilter.ALL, 0, 100);
        }

        List<String> moves(GameId id) {
            return GameRecord.of(game(id)).uciMoves();
        }
    }

    // ── Tests ───────────────────────────────────────────────────────────

    @Test
    void aFinishedGameReachesTheOtherComputerAndIsAnalysedThere() {
        var game = a.newBotGame("f2f3", "e7e5", "g2g4", "d8h4");
        assertThat(game.status()).isEqualTo(GameStatus.FINISHED);

        assertThat(a.history.enable().active()).isTrue();
        assertThat(cloud.live()).isEqualTo(1);
        b.history.enable();

        var copy = b.game(game.id());
        assertThat(copy.moves()).hasSize(4);
        assertThat(copy.outcome()).isEqualTo(game.outcome());
        assertThat(copy.version()).isEqualTo(game.version());
        assertThat(b.analyses.findByGameId(game.id())).isPresent();
        assertThat(b.history.view().pendingGames()).isZero();
        assertThat(b.history.view().syncedGames()).isEqualTo(1);
    }

    @Test
    void theFirstSyncJoinsBothSides() {
        var mine = a.newBotGame("e2e4");
        var theirs = b.newBotGame("d2d4");
        a.history.enable();
        b.history.enable();
        a.history.sync();

        assertThat(a.all()).extracting(Game::id).containsExactlyInAnyOrder(mine.id(), theirs.id());
        assertThat(b.all()).extracting(Game::id).containsExactlyInAnyOrder(mine.id(), theirs.id());
    }

    @Test
    void aGameInProgressGoesOnInTheOtherComputer() {
        var game = a.newBotGame("e2e4", "e7e5");
        a.history.enable();
        b.history.enable();

        b.play(game.id(), "g1f3", "b8c6");
        b.history.sync();
        a.history.sync();

        assertThat(a.moves(game.id())).containsExactly("e2e4", "e7e5", "g1f3", "b8c6");
        assertThat(cloud.puts).isEqualTo(2); // A's upload and B's, nothing more
        a.history.sync();
        b.history.sync();
        assertThat(cloud.puts).isEqualTo(2);
    }

    @Test
    void whenOneSideIsFurtherOnTheLongerOneWins() {
        var game = a.newBotGame("e2e4");
        a.history.enable();
        b.history.enable();

        a.play(game.id(), "e7e5"); // A moves without syncing
        b.play(game.id(), "e7e5", "g1f3"); // B goes further, and syncs first
        b.history.sync();
        a.history.sync();
        b.history.sync();

        assertThat(a.moves(game.id())).containsExactly("e2e4", "e7e5", "g1f3");
        assertThat(b.moves(game.id())).containsExactly("e2e4", "e7e5", "g1f3");
    }

    @Test
    void withTheSameMovesTheFinishedOneWins() {
        var game = a.newBotGame("e2e4");
        a.history.enable();
        b.history.enable();

        a.play(game.id(), "e7e5");
        b.play(game.id(), "e7e5");
        b.resign(game.id());
        a.history.sync(); // A uploads its version first
        b.history.sync(); // B's resignation must not be lost
        a.history.sync();

        assertThat(a.game(game.id()).status()).isEqualTo(GameStatus.FINISHED);
        assertThat(b.game(game.id()).status()).isEqualTo(GameStatus.FINISHED);
    }

    @Test
    void divergingGamesAreBothKept() {
        var game = a.newBotGame("e2e4");
        a.history.enable();
        b.history.enable();

        a.play(game.id(), "e7e5");
        b.play(game.id(), "c7c5");
        b.history.sync(); // B's line reaches the account first
        a.history.sync(); // A keeps its line and takes B's as a new game
        b.history.sync();
        a.history.sync();

        assertThat(a.all()).hasSize(2);
        assertThat(b.all()).hasSize(2);
        assertThat(a.all()).extracting(Game::id)
                .containsExactlyInAnyOrderElementsOf(b.all().stream().map(Game::id).toList());
        assertThat(b.moves(game.id())).containsExactly("e2e4", "e7e5"); // A's line under the original id
        var other = b.all().stream().filter(g -> !g.id().equals(game.id())).findFirst().orElseThrow();
        assertThat(GameRecord.of(other).uciMoves()).containsExactly("e2e4", "c7c5");
    }

    @Test
    void aDeletionReachesTheOtherComputer() {
        var game = a.newBotGame("e2e4", "e7e5");
        a.history.enable();
        b.history.enable();

        a.games.delete(game.id());
        a.history.sync();
        b.history.sync();

        assertThat(b.games.findById(game.id())).isEmpty();
        assertThat(cloud.live()).isZero();
        assertThat(b.history.view().syncedGames()).isZero();
    }

    @Test
    void aGameChangedAfterItWasDeletedElsewhereComesBack() {
        var game = a.newBotGame("e2e4");
        a.history.enable();
        b.history.enable();

        a.games.delete(game.id());
        a.history.sync();
        b.play(game.id(), "e7e5"); // B had moved without knowing
        b.history.sync();
        a.history.sync();

        assertThat(a.moves(game.id())).containsExactly("e2e4", "e7e5");
        assertThat(b.moves(game.id())).containsExactly("e2e4", "e7e5");
    }

    @Test
    void anOnlineGameGoesUpOnlyOnceItEnds() {
        var game = Game.start(new GameId(UUID.randomUUID()), new Player.Local(""), new Player.Remote("luis"),
                Position.initial(), T0).play("e2e4", com.arquitechthor.kopi.domain.chess.Color.WHITE, T0);
        a.games.insert(game);
        a.history.enable();
        assertThat(cloud.live()).isZero();
        assertThat(a.history.view().pendingGames()).isZero();

        a.resign(game.id());
        a.history.sync();
        assertThat(cloud.live()).isEqualTo(1);
    }

    @Test
    void anotherAccountSignedInPausesIt() {
        a.newBotGame("e2e4");
        a.history.enable();
        a.newBotGame("d2d4");
        a.sub = "luis";

        a.history.sync();
        var view = a.history.view();
        assertThat(view.otherAccount()).isTrue();
        assertThat(view.active()).isFalse();
        assertThat(cloud.live()).isEqualTo(1);
    }

    @Test
    void itCannotBeTurnedOnWithoutSigningIn() {
        a.sub = null;
        assertThatThrownBy(a.history::enable).isInstanceOf(KopiOnlineException.class)
                .extracting(e -> ((KopiOnlineException) e).problem()).isEqualTo(Problem.NOT_SIGNED_IN);
    }

    @Test
    void anUnreachableAccountIsShownAndTheNextSyncCarriesOn() {
        a.newBotGame("e2e4");
        a.history.enable();
        a.newBotGame("d2d4");
        cloud.down = true;

        var view = a.history.syncNow();
        assertThat(view.lastProblem()).contains("UNREACHABLE");
        assertThat(view.pendingGames()).isEqualTo(1);

        cloud.down = false;
        view = a.history.syncNow();
        assertThat(view.lastProblem()).isEmpty();
        assertThat(view.lastSyncAt()).contains(T0);
        assertThat(cloud.live()).isEqualTo(2);
    }

    @Test
    void deletingTheHistoryFromTheAccountTurnsItOffAndKeepsTheGamesHere() {
        a.newBotGame("e2e4");
        a.history.enable();

        var view = a.history.deleteFromAccount();

        assertThat(view.enabled()).isFalse();
        assertThat(cloud.entries).isEmpty();
        assertThat(a.all()).hasSize(1);
    }

    @Test
    void afterTheAccountLostItsHistoryTheGamesGoUpAgain() {
        a.newBotGame("e2e4");
        a.history.enable();
        b.history.enable();
        b.history.deleteFromAccount(); // from the other computer

        var game = a.all().getFirst();
        a.play(game.id(), "e7e5");
        a.history.sync();

        assertThat(cloud.live()).isEqualTo(1);
    }

    @Test
    void aGameThatDoesNotReplayIsSkipped() {
        a.history.enable();
        var id = new GameId(UUID.randomUUID());
        var broken = new GameRecord(id.value().toString(), "PLAYED", "LOCAL:", "BOT:PAWN", Position.initial().toFen(),
                List.of(new GameRecord.RecordedMove("e2e5", T0)), null, null, T0, null, Map.of());
        cloud.entries.put(id, new FakeAccountHistory.Stored(1, false, broken, ++cloud.seq));

        a.history.sync();

        assertThat(a.games.findById(id)).isEmpty();
        assertThat(a.history.view().lastProblem()).isEmpty();
    }

    @Test
    void aRecordRebuildsTheSameGame() {
        var game = a.newBotGame("e2e4", "e7e5", "d1h5", "b8c6", "f1c4", "g8f6", "h5f7");
        var again = GameRecord.of(game).toGame();
        assertThat(again.outcome()).isEqualTo(game.outcome());
        assertThat(again.version()).isEqualTo(game.version());
        assertThat(GameRecord.of(again)).isEqualTo(GameRecord.of(game));
        assertThat(new ArrayList<>(again.moves()).size()).isEqualTo(7);
    }
}
