package com.arquitechthor.kopi.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.arquitechthor.kopi.application.account.AccountView;
import com.arquitechthor.kopi.application.online.LobbyView;
import com.arquitechthor.kopi.application.online.OnlineUseCase.OnlineStatus;
import com.arquitechthor.kopi.application.port.KopiOnlineException.Problem;
import com.arquitechthor.kopi.domain.chess.Color;
import com.arquitechthor.kopi.domain.chess.Position;
import com.arquitechthor.kopi.domain.game.Game;
import com.arquitechthor.kopi.domain.game.GameId;
import com.arquitechthor.kopi.domain.game.Player;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/// The online pages (phase D) with a fake online use case.
@WebMvcTest
class OnlinePagesTest {

    private static final AccountView READY =
            new AccountView(true, Optional.of("ana@example.com"), Optional.of("Ana_92"), false, false);
    private static final Instant START = Instant.parse("2026-11-07T12:00:00Z");

    @Autowired
    MockMvcTester mvc;

    @Autowired
    WebAdapterTestApplication.FakeOnline online;

    @Autowired
    WebAdapterTestApplication.InMemoryGames games;

    @BeforeEach
    void reset() {
        online.reset();
        games.clear();
    }

    private Game onlineGame(Color mine) {
        var me = Player.Local.YOU;
        var friend = new Player.Remote("luis");
        var game = Game.start(new GameId(UUID.randomUUID()), mine == Color.WHITE ? me : friend,
                mine == Color.WHITE ? friend : me, Position.initial(), START);
        games.insert(game);
        online.statuses.put(game.id(), new OnlineStatus("luis", mine, Optional.empty(), false));
        return game;
    }

    @Test
    void everyPageLinksToIt() {
        assertThat(mvc.get().uri("/?lang=en")).bodyText().contains("href=\"/online\"", "Play online");
    }

    @Test
    void signedOutItSendsToTheAccountPage() {
        assertThat(mvc.get().uri("/online?lang=en")).hasStatusOk().bodyText()
                .contains("To play online you need a Kopi account", "href=\"/account\"")
                .doesNotContain("Invite a friend");
    }

    @Test
    void readyItShowsTheInvitationsAndTheGames() {
        var gameId = new GameId(UUID.randomUUID());
        online.lobby = new LobbyView(READY, Optional.empty(),
                List.of(new LobbyView.Invitation("r-1", "luis", Color.BLACK, START)),
                List.of(new LobbyView.Invitation("r-2", "eva", Color.WHITE, START)),
                List.of(new LobbyView.Invitation("r-3", "pepe", Color.WHITE, START)),
                List.of(new LobbyView.OnlineGame(gameId, "marta", Color.WHITE, 4, true, false)));

        assertThat(mvc.get().uri("/online?lang=en")).hasStatusOk().bodyText()
                .contains("You play as", "Ana_92", "Invite a friend", "action=\"/online/invite\"")
                .contains("luis invites you to a game, with Black", "/online/invitations/r-1/accept")
                .contains("Waiting for eva to answer", "/online/invitations/r-2/cancel")
                .contains("pepe declined your invitation", "/online/invitations/r-3/dismiss")
                .contains("Against marta, with White", "Your move", "/games/" + gameId)
                .contains("/online/events");
    }

    @Test
    void aProblemWithTheRelayIsSaidSo() {
        online.lobby = new LobbyView(READY, Optional.of(Problem.UNREACHABLE), List.of(), List.of(), List.of(),
                List.of());

        assertThat(mvc.get().uri("/online?lang=es")).bodyText()
                .contains("Ahora mismo no se puede conectar con Kopi").doesNotContain("Invita a un amigo");
    }

    @Test
    void invitingSendsTheIdAndMyColour() {
        assertThat(mvc.post().uri("/online/invite").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("kopiId", "luis").param("color", "BLACK"))
                .hasStatus(HttpStatus.FOUND).hasRedirectedUrl("/online")
                .flash().containsEntry("invited", "luis");
        assertThat(online.calls).containsExactly("invite luis BLACK");
    }

    @Test
    void anUnknownFriendIsRefusedWithAMessage() {
        online.failure = Problem.KOPI_ID_NOT_FOUND;

        assertThat(mvc.post().uri("/online/invite").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("kopiId", "nadie"))
                .flash().containsEntry(AccountController.ERROR, "KOPI_ID_NOT_FOUND");
    }

    @Test
    void acceptingOpensTheGame() {
        online.accepted = new GameId(UUID.fromString("00000000-0000-0000-0000-00000000abcd"));

        assertThat(mvc.post().uri("/online/invitations/r-1/accept"))
                .hasStatus(HttpStatus.FOUND).hasRedirectedUrl("/games/00000000-0000-0000-0000-00000000abcd");
    }

    @Test
    void theOtherAnswersComeBackToThePage() {
        mvc.post().uri("/online/invitations/r-1/decline").exchange();
        mvc.post().uri("/online/invitations/r-2/cancel").exchange();
        mvc.post().uri("/online/invitations/r-3/dismiss").exchange();

        assertThat(online.calls).containsExactly("decline r-1", "cancel r-2", "dismiss r-3");
    }

    @Test
    void anOnlineGameNamesTheFriendAndListensForTheirMoves() {
        var game = onlineGame(Color.WHITE);

        assertThat(mvc.get().uri("/games/{id}?lang=en", game.id())).hasStatusOk().bodyText()
                .contains("Online · against luis", "Your move", "Offer a draw")
                .contains("data-events=\"/games/" + game.id() + "/events\"", "data-live=\"/games/" + game.id() + "/live\"")
                .contains("data-orientation=\"white\"", "data-movable=\"true\"");
    }

    @Test
    void whileTheFriendThinksTheBoardWaits() {
        var game = onlineGame(Color.BLACK);

        assertThat(mvc.get().uri("/games/{id}?lang=es", game.id())).bodyText()
                .contains("Le toca a luis", "data-orientation=\"black\"", "data-movable=\"false\"");
    }

    @Test
    void aDrawOfferCanBeAnswered() {
        var game = onlineGame(Color.WHITE);
        online.statuses.put(game.id(), new OnlineStatus("luis", Color.WHITE, Optional.of(Color.BLACK), false));

        assertThat(mvc.get().uri("/games/{id}?lang=en", game.id())).bodyText()
                .contains("luis offers a draw.", "Accept the draw", "Decline the draw").doesNotContain("Offer a draw");
        assertThat(mvc.post().uri("/games/{id}/draw-accept", game.id()))
                .hasStatus(HttpStatus.FOUND).hasRedirectedUrl("/games/" + game.id());
        assertThat(online.calls).containsExactly("acceptDraw");
    }

    @Test
    void aDesyncedGameSaysSo() {
        var game = onlineGame(Color.WHITE);
        online.statuses.put(game.id(), new OnlineStatus("luis", Color.WHITE, Optional.empty(), true));

        assertThat(mvc.get().uri("/games/{id}?lang=en", game.id())).bodyText()
                .contains("out of step").doesNotContain("Offer a draw");
    }

    @Test
    void theLiveFragmentIsServedForTheFriendsMoves() {
        var game = onlineGame(Color.WHITE);

        assertThat(mvc.get().uri("/games/{id}/live?lang=en", game.id())).hasStatusOk().bodyText()
                .contains("id=\"game-live\"", "id=\"board-state\"").doesNotContain("<html");
    }

    @Test
    void theHistoryShowsTheFriend() {
        onlineGame(Color.WHITE);

        assertThat(mvc.get().uri("/games?lang=en")).bodyText().contains("luis");
    }
}
