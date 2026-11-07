package com.arquitechthor.kopi.application.online;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.arquitechthor.kopi.application.port.KopiOnlineException;
import com.arquitechthor.kopi.application.port.KopiOnlineException.Problem;
import com.arquitechthor.kopi.application.port.PvpRelay.RelayMove;
import com.arquitechthor.kopi.domain.chess.Color;
import com.arquitechthor.kopi.domain.game.Game;
import com.arquitechthor.kopi.domain.game.GameMode;
import com.arquitechthor.kopi.domain.game.GameOutcome;
import com.arquitechthor.kopi.domain.game.GameResult;
import com.arquitechthor.kopi.domain.game.GameStatus;
import com.arquitechthor.kopi.domain.game.Player;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// The plan's acceptance for phase D, through the use cases: two people on
/// two computers, each with their own Kopi Chess (games and links in
/// memory), playing through one relay that knows no chess (ADR-020).
class TwoComputersTest {

    /// A clock the test moves forward.
    static final class MovingClock extends Clock {
        Instant now = Instant.parse("2026-11-07T12:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }
    }

    private final MovingClock clock = new MovingClock();
    private final FakeRelay relay = new FakeRelay(clock);
    private final KopiChessInstance ana = new KopiChessInstance(relay, "sub-ana", "Ana_92", clock, 1);
    private final KopiChessInstance luis = new KopiChessInstance(relay, "sub-luis", "luis", clock, 2);

    /// Ana invites Luis with White; Luis accepts. Returns both games.
    private Game[] started() {
        ana.online.invite("luis", Optional.of(Color.WHITE));
        var invitation = luis.online.lobby().received().getFirst();
        luis.online.accept(invitation.relayGameId());
        ana.online.sync();
        return new Game[] {ana.onlyGame(), luis.onlyGame()};
    }

    @Nested
    class Invitations {

        @Test
        void theInvitationReachesTheFriendAndAcceptingStartsTheGameOnBothSides() {
            ana.online.invite(" luis ", Optional.of(Color.WHITE));

            var anaLobby = ana.online.lobby();
            assertThat(anaLobby.sent()).singleElement()
                    .satisfies(i -> assertThat(i.opponentKopiId()).isEqualTo("luis"));
            var received = luis.online.lobby().received();
            assertThat(received).singleElement().satisfies(i -> {
                assertThat(i.opponentKopiId()).isEqualTo("Ana_92");
                assertThat(i.myColor()).isEqualTo(Color.BLACK);
            });

            var luisGameId = luis.online.accept(received.getFirst().relayGameId());
            var luisGame = luis.game(luisGameId);
            assertThat(luisGame.mode()).isEqualTo(GameMode.ONLINE);
            assertThat(luisGame.player(Color.WHITE)).hasValue(new Player.Remote("Ana_92"));
            assertThat(luisGame.humanColor()).hasValue(Color.BLACK);

            var anaGames = ana.online.lobby().games();
            assertThat(anaGames).singleElement().satisfies(g -> {
                assertThat(g.opponentKopiId()).isEqualTo("luis");
                assertThat(g.myTurn()).isTrue();
            });
        }

        @Test
        void aDeclinedInvitationIsShownUntilDismissedThenGone() {
            ana.online.invite("luis", Optional.empty());
            luis.online.decline(luis.online.lobby().received().getFirst().relayGameId());

            var declined = ana.online.lobby().declined();
            assertThat(declined).hasSize(1);
            ana.online.dismiss(declined.getFirst().relayGameId());

            assertThat(relay.games).isEmpty();
            assertThat(ana.online.lobby().declined()).isEmpty();
        }

        @Test
        void aNewInvitationTellsTheOnlinePageOnce() {
            luis.online.sync();
            var before = luis.lobbyChanges;

            ana.online.invite("luis", Optional.empty());
            luis.online.sync(); // as when the relay's notification arrives
            luis.online.sync(); // the next periodic sync: nothing new

            assertThat(luis.lobbyChanges).isEqualTo(before + 1);
        }

        @Test
        void aDeclinedInvitationTellsTheInvitersPage() {
            ana.online.invite("luis", Optional.empty());
            ana.online.sync();
            var before = ana.lobbyChanges;

            luis.online.decline(luis.online.lobby().received().getFirst().relayGameId());
            ana.online.sync();

            assertThat(ana.lobbyChanges).isEqualTo(before + 1);
        }

        @Test
        void aCancelledInvitationDisappears() {
            ana.online.invite("luis", Optional.empty());
            ana.online.cancel(ana.online.lobby().sent().getFirst().relayGameId());

            assertThat(luis.online.lobby().received()).isEmpty();
        }
    }

    @Nested
    class Playing {

        @Test
        void eachMoveGoesThroughTheRelayAndIsValidatedOnTheOtherSide() {
            var games = started();

            ana.play(games[0].id(), "e2e4");
            assertThat(relay.games.values().iterator().next().moves).hasSize(1);
            luis.online.sync();
            assertThat(luis.game(games[1].id()).moves()).extracting(m -> m.move().toUci()).containsExactly("e2e4");
            assertThat(luis.game(games[1].id()).isLocalToMove()).isTrue();
            assertThat(luis.changed).contains(games[1].id());

            luis.play(games[1].id(), "e7e5");
            ana.online.sync();
            assertThat(ana.game(games[0].id()).moves()).hasSize(2);
            assertThat(ana.game(games[0].id()).currentPosition())
                    .isEqualTo(luis.game(games[1].id()).currentPosition());
        }

        @Test
        void playingOutOfTurnIsRefusedHereWithoutAskingTheRelay() {
            var games = started();

            assertThatThrownBy(() -> luis.play(games[1].id(), "e7e5"))
                    .isInstanceOf(com.arquitechthor.kopi.domain.game.NotYourTurnException.class);
            assertThat(relay.games.values().iterator().next().moves).isEmpty();
        }

        @Test
        void aMoveFromAnotherComputerOfTheSameAccountWinsAndThisOneCatchesUp() {
            var games = started();
            // Ana plays e4 from her other computer, straight to the relay.
            var stored = relay.games.values().iterator().next();
            var e4 = ana.game(games[0].id()).play("e2e4", Color.WHITE, clock.instant());
            stored.moves.add(new RelayMove(1, "e2e4", OnlineGames.zobrist(e4), clock.instant()));
            stored.lastMoveAt = clock.instant();

            assertThatThrownBy(() -> ana.play(games[0].id(), "d2d4"))
                    .isInstanceOfSatisfying(KopiOnlineException.class,
                            e -> assertThat(e.problem()).isEqualTo(Problem.NOT_YOUR_TURN));
            assertThat(ana.game(games[0].id()).moves()).extracting(m -> m.move().toUci()).containsExactly("e2e4");
        }

        @Test
        void aMoveThatDoesNotFitDesyncsTheGame() {
            var games = started();
            var stored = relay.games.values().iterator().next();
            stored.moves.add(new RelayMove(1, "e2e4", "0000000000000000", clock.instant())); // wrong hash
            stored.lastMoveAt = clock.instant();

            luis.online.sync();

            assertThat(luis.online.status(games[1].id())).hasValueSatisfying(s -> assertThat(s.desynced()).isTrue());
            assertThat(luis.game(games[1].id()).moves()).isEmpty();
            assertThat(luis.online.lobby().games().getFirst().desynced()).isTrue();
        }

        @Test
        void anIllegalMoveDesyncsTheGameToo() {
            var games = started();
            var stored = relay.games.values().iterator().next();
            stored.moves.add(new RelayMove(1, "e2e5", "0123456789abcdef", clock.instant()));

            luis.online.sync();

            assertThat(luis.online.status(games[1].id()).orElseThrow().desynced()).isTrue();
            assertThatThrownBy(() -> luis.play(games[1].id(), "e7e5")).isInstanceOf(Exception.class);
        }
    }

    @Nested
    class Endings {

        @Test
        void aMateIsSeenByBothAndTheRelayForgetsTheGame() {
            var games = started();
            ana.play(games[0].id(), "f2f3");
            luis.online.sync();
            luis.play(games[1].id(), "e7e5");
            ana.online.sync();
            ana.play(games[0].id(), "g2g4");
            luis.online.sync();

            var mated = luis.play(games[1].id(), "d8h4");
            assertThat(mated.outcome()).hasValue(new GameOutcome.Checkmate(Color.BLACK));
            assertThat(relay.games).hasSize(1); // Ana has not seen it yet

            ana.online.sync();
            var anaGame = ana.game(games[0].id());
            assertThat(anaGame.outcome()).hasValue(new GameOutcome.Checkmate(Color.BLACK));
            assertThat(relay.games).isEmpty();
            assertThat(ana.analyses.findByGameId(anaGame.id())).isPresent();
            assertThat(luis.analyses.findByGameId(games[1].id())).isPresent();
        }

        @Test
        void aResignationReachesTheOtherSide() {
            var games = started();
            ana.play(games[0].id(), "e2e4");

            luis.resign(games[1].id());
            ana.online.sync();

            assertThat(ana.game(games[0].id()).outcome()).hasValue(new GameOutcome.Resignation(Color.BLACK));
            assertThat(ana.game(games[0].id()).result()).hasValue(GameResult.WHITE_WIN);
            assertThat(relay.games).isEmpty();
        }

        @Test
        void aDrawIsOfferedAndAccepted() {
            var games = started();

            ana.online.offerDraw(games[0].id());
            luis.online.sync();
            assertThat(luis.online.status(games[1].id()).orElseThrow().drawOffered()).isTrue();
            assertThat(ana.online.status(games[0].id()).orElseThrow().drawPending()).isTrue();

            luis.online.acceptDraw(games[1].id());
            ana.online.sync();

            assertThat(ana.game(games[0].id()).outcome()).hasValue(new GameOutcome.DrawByAgreement());
            assertThat(luis.game(games[1].id()).result()).hasValue(GameResult.DRAW);
            assertThat(relay.games).isEmpty();
        }

        @Test
        void aDeclinedOfferGoesAway() {
            var games = started();
            ana.online.offerDraw(games[0].id());
            luis.online.sync();

            luis.online.declineDraw(games[1].id());
            ana.online.sync();

            assertThat(ana.online.status(games[0].id()).orElseThrow().drawOfferBy()).isEmpty();
            assertThat(ana.game(games[0].id()).status()).isEqualTo(GameStatus.IN_PROGRESS);
        }

        @Test
        void thirtyDaysWithoutMovesEndTheGameTheSameWayOnBothComputers() {
            var games = started();
            ana.play(games[0].id(), "e2e4");
            luis.online.sync();

            clock.advance(Duration.ofDays(31));
            ana.online.sync();
            luis.online.sync();

            assertThat(ana.game(games[0].id()).outcome()).hasValue(new GameOutcome.Inactivity(Color.WHITE));
            assertThat(luis.game(games[1].id()).outcome()).hasValue(new GameOutcome.Inactivity(Color.WHITE));
        }

        @Test
        void anotherAccountsGamesAreLeftAlone() {
            var games = started();
            luis.sub = "sub-someone-else";

            luis.online.sync();

            assertThat(luis.game(games[1].id()).status()).isEqualTo(GameStatus.IN_PROGRESS);
        }
    }
}
