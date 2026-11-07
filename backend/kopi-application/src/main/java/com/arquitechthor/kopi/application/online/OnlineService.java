package com.arquitechthor.kopi.application.online;

import com.arquitechthor.kopi.application.account.KopiAccountUseCase;
import com.arquitechthor.kopi.application.game.GameNotFoundException;
import com.arquitechthor.kopi.application.game.GameView;
import com.arquitechthor.kopi.application.port.GameRepository;
import com.arquitechthor.kopi.application.port.KopiAccount;
import com.arquitechthor.kopi.application.port.KopiOnlineException;
import com.arquitechthor.kopi.application.port.KopiOnlineException.Problem;
import com.arquitechthor.kopi.application.port.OnlineEvents;
import com.arquitechthor.kopi.domain.chess.Color;
import com.arquitechthor.kopi.domain.game.Game;
import com.arquitechthor.kopi.domain.game.GameId;
import com.arquitechthor.kopi.domain.game.GameMode;
import com.arquitechthor.kopi.domain.game.GameStatus;
import com.arquitechthor.kopi.domain.game.Player;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

/// [OnlineUseCase] over [OnlineGames], the Kopi account and the relay.
public final class OnlineService implements OnlineUseCase {

    private static final System.Logger log = System.getLogger(OnlineService.class.getName());

    private final OnlineGames online;
    private final KopiAccount account;
    private final KopiAccountUseCase accounts;
    private final GameRepository games;
    private final OnlineEvents events;

    public OnlineService(OnlineGames online, KopiAccount account, KopiAccountUseCase accounts, GameRepository games,
            OnlineEvents events) {
        this.online = Objects.requireNonNull(online, "online");
        this.account = Objects.requireNonNull(account, "account");
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.games = Objects.requireNonNull(games, "games");
        this.events = Objects.requireNonNull(events, "events");
    }

    @Override
    public LobbyView lobby() {
        var view = accounts.account();
        if (!view.readyToPlay()) {
            return LobbyView.of(view, Optional.empty());
        }
        var sub = account.session().orElseThrow().sub();
        try {
            var remote = online.syncAll(sub);
            var received = new ArrayList<LobbyView.Invitation>();
            var sent = new ArrayList<LobbyView.Invitation>();
            var declined = new ArrayList<LobbyView.Invitation>();
            for (var game : remote) {
                var invitation = new LobbyView.Invitation(game.id(), game.opponentKopiId(), game.myColor(),
                        game.createdAt());
                switch (game.status()) {
                    case INVITED -> (game.invitedByMe() ? sent : received).add(invitation);
                    case DECLINED -> {
                        if (game.invitedByMe()) {
                            declined.add(invitation);
                        }
                    }
                    case ACTIVE, FINISHED -> { /* shown as games */ }
                }
            }
            var playing = new ArrayList<LobbyView.OnlineGame>();
            for (var link : online.links().inProgress(sub)) {
                games.findById(link.gameId()).ifPresent(game -> playing.add(new LobbyView.OnlineGame(game.id(),
                        opponent(game), game.humanColor().orElseThrow(), game.moves().size(), game.isLocalToMove(),
                        link.desynced())));
            }
            playing.sort(Comparator.comparing(LobbyView.OnlineGame::myTurn).reversed());
            return new LobbyView(view, Optional.empty(), received, sent, declined, playing);
        } catch (KopiOnlineException e) {
            return LobbyView.of(view, Optional.of(e.problem()));
        }
    }

    @Override
    public void invite(String kopiId, Optional<Color> color) {
        requireReady();
        online.relay().invite(kopiId == null ? "" : kopiId.strip(), color.orElse(null), Optional.empty());
        events.lobbyChanged();
    }

    @Override
    public GameId accept(String relayGameId) {
        var sub = requireReady();
        var answer = online.relay().accept(relayGameId);
        var game = online.sync(answer, sub)
                .orElseThrow(() -> new KopiOnlineException(Problem.OTHER, "The accepted game did not start"));
        events.lobbyChanged();
        return game.id();
    }

    @Override
    public void decline(String relayGameId) {
        requireReady();
        online.relay().decline(relayGameId);
        events.lobbyChanged();
    }

    @Override
    public void cancel(String relayGameId) {
        requireReady();
        online.relay().cancel(relayGameId);
        events.lobbyChanged();
    }

    @Override
    public void dismiss(String relayGameId) {
        requireReady();
        online.relay().acknowledge(relayGameId);
        events.lobbyChanged();
    }

    @Override
    public void sync() {
        var session = account.session();
        if (session.isEmpty()) {
            return;
        }
        try {
            online.syncAll(session.get().sub());
        } catch (KopiOnlineException e) {
            // Not signed in any more, no Kopi id yet, no network: next time.
            log.log(System.Logger.Level.DEBUG, "Online games not synced: " + e.problem());
        }
    }

    @Override
    public Optional<OnlineStatus> status(GameId gameId) {
        return online.links().findByGame(gameId).flatMap(link -> games.findById(gameId).map(game ->
                new OnlineStatus(opponent(game), game.humanColor().orElseThrow(),
                        game.status() == GameStatus.IN_PROGRESS ? link.drawOfferBy() : Optional.empty(),
                        link.desynced())));
    }

    @Override
    public GameView offerDraw(GameId gameId) {
        return GameView.of(online.offerDraw(onlineGame(gameId)));
    }

    @Override
    public GameView acceptDraw(GameId gameId) {
        return GameView.of(online.acceptDraw(onlineGame(gameId)));
    }

    @Override
    public GameView declineDraw(GameId gameId) {
        return GameView.of(online.declineDraw(onlineGame(gameId)));
    }

    private Game onlineGame(GameId gameId) {
        var game = games.findById(gameId).orElseThrow(() -> new GameNotFoundException(gameId));
        if (game.mode() != GameMode.ONLINE) {
            throw new IllegalArgumentException("Game " + gameId + " is not online");
        }
        return game;
    }

    /// The account's `sub`, if it can play online.
    private String requireReady() {
        var session = account.session()
                .orElseThrow(() -> new KopiOnlineException(Problem.NOT_SIGNED_IN, "Sign in with Kopi first"));
        if (account.kopiId().isEmpty()) {
            throw new KopiOnlineException(Problem.NO_KOPI_ID, "Choose a Kopi id first");
        }
        return session.sub();
    }

    private static String opponent(Game game) {
        var color = game.humanColor().orElseThrow().opposite();
        return game.player(color).map(player -> ((Player.Remote) player).kopiId()).orElse("?");
    }
}
