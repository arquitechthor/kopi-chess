package com.arquitechthor.kopi.application.online;

import com.arquitechthor.kopi.application.game.AnalysisOnFinish;
import com.arquitechthor.kopi.application.game.GameNotFoundException;
import com.arquitechthor.kopi.application.port.AnalysisJobQueue;
import com.arquitechthor.kopi.application.port.GameRepository;
import com.arquitechthor.kopi.application.port.IdGenerator;
import com.arquitechthor.kopi.application.port.KopiOnlineException;
import com.arquitechthor.kopi.application.port.KopiOnlineException.Problem;
import com.arquitechthor.kopi.application.port.OnlineEvents;
import com.arquitechthor.kopi.application.port.OnlineGameStore;
import com.arquitechthor.kopi.application.port.OnlineGameStore.OnlineLink;
import com.arquitechthor.kopi.application.port.PvpRelay;
import com.arquitechthor.kopi.application.port.PvpRelay.RelayGame;
import com.arquitechthor.kopi.domain.chess.Color;
import com.arquitechthor.kopi.domain.chess.Fen;
import com.arquitechthor.kopi.domain.chess.Position;
import com.arquitechthor.kopi.domain.game.Game;
import com.arquitechthor.kopi.domain.game.GameMode;
import com.arquitechthor.kopi.domain.game.GameOutcome;
import com.arquitechthor.kopi.domain.game.GameRuleException;
import com.arquitechthor.kopi.domain.game.GameStatus;
import com.arquitechthor.kopi.domain.game.Player;
import java.time.Clock;
import java.time.Duration;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/// Keeping the games here in step with the relay (ADR-020, plan of player
/// versus player §6), and playing in them.
///
/// - **Every move from the relay is validated here**, with the domain, as if
///   it had been played on this board, and its Zobrist hash compared with the
///   one that came with it. If either fails, the game is **desynced**: it
///   stops, with a warning, and can be resigned.
/// - **My move goes to the relay first** and is saved here only once the
///   relay accepts it, so both sides always have the same moves.
/// - **The end is confirmed** to the relay once this side has it, so the
///   relay can forget the game.
/// - **30 days without moves**, or a game the relay no longer has, ends by
///   inactivity: whoever moved last wins. Both sides reach the same result
///   from the same moves.
///
/// Shared by the online use cases; it is not a use case itself.
public final class OnlineGames {

    /// After this long without moves an online game is over (plan §1).
    public static final Duration INACTIVITY = Duration.ofDays(30);

    private final PvpRelay relay;
    private final OnlineGameStore links;
    private final GameRepository games;
    private final IdGenerator ids;
    private final AnalysisJobQueue analyses;
    private final OnlineEvents events;
    private final Clock clock;

    public OnlineGames(PvpRelay relay, OnlineGameStore links, GameRepository games, IdGenerator ids,
            AnalysisJobQueue analyses, OnlineEvents events, Clock clock) {
        this.relay = Objects.requireNonNull(relay, "relay");
        this.links = Objects.requireNonNull(links, "links");
        this.games = Objects.requireNonNull(games, "games");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.analyses = Objects.requireNonNull(analyses, "analyses");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    PvpRelay relay() {
        return relay;
    }

    OnlineGameStore links() {
        return links;
    }

    /// Brings every game of `accountSub` in step with the relay, and returns
    /// what the relay has (invitations included), for the lobby.
    synchronized List<RelayGame> syncAll(String accountSub) {
        var remote = relay.myGames();
        var seen = new HashSet<String>();
        for (var game : remote) {
            seen.add(game.id());
            sync(game, accountSub);
        }
        // Games in progress here that the relay no longer has, or that have
        // gone cold: over by inactivity.
        var now = clock.instant();
        for (var link : links.inProgress(accountSub)) {
            if (!seen.contains(link.relayGameId()) || link.lastMoveAt().plus(INACTIVITY).isBefore(now)) {
                endByInactivity(link);
            }
        }
        return remote;
    }

    /// Brings one relay game in step: creates it here the first time it is
    /// `ACTIVE`, applies the moves it does not have yet, then the ending.
    synchronized Optional<Game> sync(RelayGame summary, String accountSub) {
        var existing = links.findByRelay(summary.id());
        if (existing.isEmpty() && summary.status() != PvpRelay.Status.ACTIVE
                && summary.status() != PvpRelay.Status.FINISHED) {
            return Optional.empty(); // an invitation: it lives only in the relay
        }
        var link = existing.orElseGet(() -> create(summary, accountSub));
        if (!link.accountSub().equals(accountSub)) {
            return Optional.empty(); // another account's game: left alone
        }
        var loaded = games.findById(link.gameId()).orElseThrow(() -> new GameNotFoundException(link.gameId()));
        if (link.desynced()) {
            return Optional.of(loaded);
        }

        var game = loaded;
        var desynced = false;
        if (summary.lastPly() > game.moves().size()) {
            var full = relay.game(summary.id(), game.moves().size() + 1);
            for (var move : full.moves().stream().sorted(Comparator.comparingInt(PvpRelay.RelayMove::ply)).toList()) {
                if (move.ply() != game.moves().size() + 1) {
                    continue;
                }
                try {
                    game = game.play(move.uci(), game.currentPosition().sideToMove(), move.at());
                } catch (GameRuleException _) {
                    desynced = true;
                    break;
                }
                if (!zobrist(game).equals(move.zobrist())) {
                    desynced = true;
                    break;
                }
            }
        }
        if (!desynced && summary.status() == PvpRelay.Status.FINISHED) {
            var ended = applyEnding(game, summary);
            if (ended.isEmpty()) {
                desynced = true;
            } else {
                game = ended.get();
            }
        }
        if (desynced) {
            links.save(link.withDesynced());
            events.gameChanged(link.gameId());
            return Optional.of(loaded);
        }

        var changed = game.version() != loaded.version();
        if (changed) {
            games.update(game, loaded.version());
            AnalysisOnFinish.request(analyses, game, clock.instant());
        }
        var updated = link.with(summary.lastMoveAt().orElse(summary.createdAt()),
                summary.status() == PvpRelay.Status.ACTIVE ? summary.drawOfferBy() : Optional.empty());
        if (summary.status() == PvpRelay.Status.FINISHED && !updated.acknowledged()) {
            relay.acknowledge(summary.id());
            updated = updated.withAcknowledged();
        }
        if (!updated.equals(link) || existing.isEmpty()) {
            links.save(updated);
        }
        if (changed || !updated.equals(link)) {
            events.gameChanged(game.id());
        }
        return Optional.of(game);
    }

    /// Brings one game here in step with its relay game, if it is online.
    synchronized void sync(Game game) {
        links.findByGame(game.id()).ifPresent(link -> sync(relay.game(link.relayGameId(), 1), link.accountSub()));
    }

    /// The game here for a relay game seen for the first time.
    private OnlineLink create(RelayGame summary, String accountSub) {
        var me = Player.Local.YOU;
        var friend = new Player.Remote(summary.opponentKopiId());
        var white = summary.myColor() == Color.WHITE ? me : friend;
        var black = summary.myColor() == Color.WHITE ? friend : me;
        var position = summary.initialFen().map(Fen::parse).orElse(Position.initial());
        var game = Game.start(ids.newGameId(), white, black, position, summary.createdAt());
        games.insert(game);
        var link = new OnlineLink(game.id(), summary.id(), accountSub, false,
                summary.lastMoveAt().orElse(summary.createdAt()), Optional.empty(), false);
        links.save(link);
        events.lobbyChanged();
        return link;
    }

    /// The game after the relay's ending, or empty if it does not fit what
    /// the moves show here.
    private Optional<Game> applyEnding(Game game, RelayGame summary) {
        var termination = summary.termination().orElse("");
        var now = clock.instant();
        if (game.status() == GameStatus.IN_PROGRESS) {
            return switch (termination) {
                case "RESIGNATION" -> summary.finishedBy().map(resigned -> game.resign(resigned, now));
                case "DRAW_AGREEMENT" -> Optional.of(game.agreeDraw(now));
                // An ending by the rules must already show on this board.
                default -> Optional.empty();
            };
        }
        // Finished here too: it must be the same result.
        var result = game.result().map(OnlineGames::resultText).orElse("");
        return result.equals(summary.result().orElse("")) ? Optional.of(game) : Optional.empty();
    }

    /// Ends `link`'s game by inactivity, if it is still in progress.
    private void endByInactivity(OnlineLink link) {
        var loaded = games.findById(link.gameId());
        if (loaded.isEmpty() || loaded.get().status() != GameStatus.IN_PROGRESS
                || loaded.get().mode() != GameMode.ONLINE) {
            return;
        }
        var game = loaded.get().endByInactivity(clock.instant());
        games.update(game, loaded.get().version());
        links.save(link.withAcknowledged());
        AnalysisOnFinish.request(analyses, game, clock.instant());
        events.gameChanged(game.id());
    }

    /// Plays my move: first in the relay, then here.
    ///
    /// @throws OnlineGameDesyncedException if the game is desynced
    /// @throws KopiOnlineException         if the relay refuses it (after a
    ///                                     `WRONG_PLY`, the game here is
    ///                                     brought in step first)
    public synchronized Game play(Game loaded, String uci) {
        var link = linkOf(loaded);
        var after = loaded.play(uci, loaded.humanColor().orElseThrow(), clock.instant());
        var played = after.moves().getLast();
        var ending = after.outcome().map(OnlineGames::ending);
        try {
            relay.move(link.relayGameId(), played.ply(), played.move().toUci(), zobrist(after), ending);
        } catch (KopiOnlineException e) {
            if (e.problem() == Problem.WRONG_PLY || e.problem() == Problem.NOT_YOUR_TURN
                    || e.problem() == Problem.WRONG_STATUS) {
                sync(loaded);
            }
            throw e;
        }
        games.update(after, loaded.version());
        var offer = link.drawOfferBy().filter(color -> color == loaded.humanColor().orElseThrow());
        var updated = link.with(clock.instant(), offer);
        links.save(after.status() == GameStatus.FINISHED ? updated.withAcknowledged() : updated);
        AnalysisOnFinish.request(analyses, after, clock.instant());
        return after;
    }

    /// I resign: first in the relay, then here.
    public synchronized Game resign(Game loaded) {
        var link = linkOf(loaded);
        if (loaded.status() != GameStatus.IN_PROGRESS) {
            return loaded.resign(loaded.humanColor().orElseThrow(), clock.instant()); // refused by the domain
        }
        relay.resign(link.relayGameId());
        var after = loaded.resign(loaded.humanColor().orElseThrow(), clock.instant());
        games.update(after, loaded.version());
        links.save(link.with(clock.instant(), Optional.empty()).withAcknowledged());
        AnalysisOnFinish.request(analyses, after, clock.instant());
        return after;
    }

    /// I offer a draw; if the friend had offered one already, it is agreed.
    synchronized Game offerDraw(Game loaded) {
        var link = linkOf(loaded);
        requireInProgress(loaded);
        var answer = relay.offerDraw(link.relayGameId());
        if (answer.status() == PvpRelay.Status.FINISHED) {
            return agreed(loaded, link);
        }
        links.save(link.with(link.lastMoveAt(), answer.drawOfferBy()));
        return loaded;
    }

    /// I accept the friend's offer.
    synchronized Game acceptDraw(Game loaded) {
        var link = linkOf(loaded);
        requireInProgress(loaded);
        relay.acceptDraw(link.relayGameId());
        return agreed(loaded, link);
    }

    /// I decline the friend's offer.
    synchronized Game declineDraw(Game loaded) {
        var link = linkOf(loaded);
        requireInProgress(loaded);
        relay.declineDraw(link.relayGameId());
        links.save(link.with(link.lastMoveAt(), Optional.empty()));
        return loaded;
    }

    private Game agreed(Game loaded, OnlineLink link) {
        var after = loaded.agreeDraw(clock.instant());
        games.update(after, loaded.version());
        links.save(link.with(link.lastMoveAt(), Optional.empty()).withAcknowledged());
        AnalysisOnFinish.request(analyses, after, clock.instant());
        return after;
    }

    private static void requireInProgress(Game game) {
        if (game.status() != GameStatus.IN_PROGRESS) {
            throw new KopiOnlineException(Problem.WRONG_STATUS, "Game " + game.id() + " is over");
        }
    }

    private OnlineLink linkOf(Game game) {
        var link = links.findByGame(game.id())
                .orElseThrow(() -> new IllegalStateException("Online game " + game.id() + " has no link"));
        if (link.desynced()) {
            throw new OnlineGameDesyncedException(game.id());
        }
        return link;
    }

    /// The Zobrist hash of the game's current position as the relay keeps
    /// it: 16 lower-case hexadecimal digits.
    static String zobrist(Game game) {
        return String.format(Locale.ROOT, "%016x", game.currentPosition().zobristHash());
    }

    /// How a move ends the game by the rules, as the relay records it.
    static PvpRelay.Ending ending(GameOutcome outcome) {
        var result = resultText(outcome.result());
        return switch (outcome) {
            case GameOutcome.Checkmate _ -> new PvpRelay.Ending(result, "CHECKMATE");
            case GameOutcome.Stalemate _ -> new PvpRelay.Ending(result, "STALEMATE");
            case GameOutcome.InsufficientMaterial _ -> new PvpRelay.Ending(result, "INSUFFICIENT_MATERIAL");
            case GameOutcome.ThreefoldRepetition _ -> new PvpRelay.Ending(result, "THREEFOLD_REPETITION");
            case GameOutcome.FiftyMoveRule _ -> new PvpRelay.Ending(result, "FIFTY_MOVE_RULE");
            case GameOutcome.Resignation _, GameOutcome.DrawByAgreement _, GameOutcome.Inactivity _,
                 GameOutcome.Declared _ -> throw new IllegalArgumentException("Not an ending by a move: " + outcome);
        };
    }

    static String resultText(com.arquitechthor.kopi.domain.game.GameResult result) {
        return switch (result) {
            case WHITE_WIN -> "1-0";
            case BLACK_WIN -> "0-1";
            case DRAW -> "1/2-1/2";
        };
    }
}
