package com.arquitechthor.kopi.application.online;

import com.arquitechthor.kopi.application.account.KopiAccountService;
import com.arquitechthor.kopi.application.analysis.InMemoryAnalysisJobQueue;
import com.arquitechthor.kopi.application.game.GameView;
import com.arquitechthor.kopi.application.game.InMemoryGameRepository;
import com.arquitechthor.kopi.application.game.PlayMove;
import com.arquitechthor.kopi.application.game.PlayMoveCommand;
import com.arquitechthor.kopi.application.game.Resign;
import com.arquitechthor.kopi.application.game.ResignCommand;
import com.arquitechthor.kopi.application.port.KopiAccount;
import com.arquitechthor.kopi.application.port.OnlineEvents;
import com.arquitechthor.kopi.application.port.OnlineGameStore;
import com.arquitechthor.kopi.domain.game.Game;
import com.arquitechthor.kopi.domain.game.GameId;
import java.net.URI;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/// One Kopi Chess on one computer, with its own games and links in memory,
/// signed in to `sub` with `kopiId` and talking to the shared [FakeRelay].
final class KopiChessInstance {

    final InMemoryGameRepository games = new InMemoryGameRepository();
    final MemoryLinks links = new MemoryLinks();
    final InMemoryAnalysisJobQueue analyses = new InMemoryAnalysisJobQueue();
    final List<GameId> changed = new ArrayList<>();
    int lobbyChanges;
    final OnlineService online;
    private final PlayMove playMove;
    private final Resign resign;
    String sub;

    KopiChessInstance(FakeRelay relay, String sub, String kopiId, Clock clock, long idPrefix) {
        this.sub = sub;
        relay.register(sub, kopiId);
        var ids = new AtomicLong();
        var events = new OnlineEvents() {
            @Override
            public void gameChanged(GameId gameId) {
                changed.add(gameId);
            }

            @Override
            public void lobbyChanged() {
                lobbyChanges++;
            }
        };
        var account = new FixedAccount(this, kopiId);
        var onlineGames = new OnlineGames(relay.forAccount(sub), links, games,
                () -> new GameId(new UUID(idPrefix, ids.incrementAndGet())), analyses, events, clock);
        online = new OnlineService(onlineGames, account, new KopiAccountService(account), games, events);
        playMove = new PlayMove(games, (position, profile) -> {
            throw new AssertionError("No bot online");
        }, analyses, clock, onlineGames);
        resign = new Resign(games, analyses, clock, onlineGames);
    }

    Game game(GameId id) {
        return games.findById(id).orElseThrow();
    }

    /// The only online game here.
    Game onlyGame() {
        var all = games.findRecent(com.arquitechthor.kopi.application.port.GameFilter.ALL, 0, 10);
        if (all.size() != 1) {
            throw new AssertionError("Expected one game, found " + all.size());
        }
        return all.getFirst();
    }

    GameView play(GameId id, String uci) {
        return playMove.play(new PlayMoveCommand(id, uci, game(id).version()));
    }

    GameView resign(GameId id) {
        return resign.resign(new ResignCommand(id, game(id).version()));
    }

    /// A Kopi account always signed in, with an id.
    private record FixedAccount(KopiChessInstance pc, String id) implements KopiAccount {
        @Override
        public URI beginLogin() {
            return URI.create("https://login.example");
        }

        @Override
        public void completeLogin(String code, String state) {}

        @Override
        public Optional<Session> session() {
            return Optional.of(new Session(pc.sub, ""));
        }

        @Override
        public Optional<String> kopiId() {
            return Optional.of(id);
        }

        @Override
        public String reserveKopiId(String kopiId) {
            return id;
        }

        @Override
        public Optional<String> findKopiId(String kopiId) {
            return Optional.of(kopiId);
        }

        @Override
        public URI signOut() {
            return URI.create("https://login.example/logout");
        }
    }

    /// The links in memory, as the database adapter keeps them.
    final class MemoryLinks implements OnlineGameStore {
        final Map<GameId, OnlineLink> byGame = new LinkedHashMap<>();

        @Override
        public Optional<OnlineLink> findByGame(GameId gameId) {
            return Optional.ofNullable(byGame.get(gameId));
        }

        @Override
        public Optional<OnlineLink> findByRelay(String relayGameId) {
            return byGame.values().stream().filter(l -> l.relayGameId().equals(relayGameId)).findFirst();
        }

        @Override
        public List<OnlineLink> inProgress(String accountSub) {
            return byGame.values().stream().filter(l -> l.accountSub().equals(accountSub))
                    .filter(l -> games.findById(l.gameId()).map(g -> g.outcome().isEmpty()).orElse(false))
                    .toList();
        }

        @Override
        public void save(OnlineLink link) {
            byGame.put(link.gameId(), link);
        }
    }
}
