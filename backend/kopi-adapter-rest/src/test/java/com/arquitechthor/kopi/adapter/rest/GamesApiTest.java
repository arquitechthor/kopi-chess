package com.arquitechthor.kopi.adapter.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.arquitechthor.kopi.domain.chess.Fen;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@WebMvcTest
class GamesApiTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    RestAdapterTestApplication.InMemoryGames games;

    @BeforeEach
    void noGames() {
        games.clear();
    }

    private MvcTestResult create(String humanColor, String botProfile) {
        return mvc.post().uri("/api/v1/games")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"humanColor\": \"" + humanColor + "\", \"botProfile\": \"" + botProfile + "\"}")
                .exchange();
    }

    /// The id of a new game against `PAWN`, the person playing White.
    private String newGame() {
        create("WHITE", "PAWN");
        return games.findRecent(0, 1).getFirst().id().toString();
    }

    private MvcTestResult move(String id, String ifMatch, String uci) {
        var request = mvc.post().uri("/api/v1/games/{id}/moves", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"uci\": \"" + uci + "\"}");
        if (ifMatch != null) {
            request = request.header(HttpHeaders.IF_MATCH, ifMatch);
        }
        return request.exchange();
    }

    @Nested
    class Create {

        @Test
        void returnsTheNewGameWithItsLocationAndVersion() {
            var result = create("WHITE", "PAWN");
            var id = games.findRecent(0, 1).getFirst().id().toString();

            assertThat(result).hasStatus(HttpStatus.CREATED)
                    .hasHeader(HttpHeaders.LOCATION, "/api/v1/games/" + id)
                    .hasHeader(HttpHeaders.ETAG, "\"0\"");
            assertThat(result).bodyJson()
                    .hasPathSatisfying("$.id", json -> json.assertThat().isEqualTo(id))
                    .hasPathSatisfying("$.status", status -> status.assertThat().isEqualTo("IN_PROGRESS"))
                    .hasPathSatisfying("$.fen", fen -> fen.assertThat().isEqualTo(Fen.INITIAL))
                    .hasPathSatisfying("$.humanToMove", turn -> turn.assertThat().isEqualTo(true))
                    .hasPathSatisfying("$.legalMoves.length()", n -> n.assertThat().isEqualTo(20))
                    .hasPathSatisfying("$.lastMove", last -> last.assertThat().isNull())
                    .hasPathSatisfying("$.outcome", outcome -> outcome.assertThat().isNull())
                    .hasPathSatisfying("$.createdAt", at -> at.assertThat().isEqualTo("2026-10-23T12:00:00Z"));
        }

        @Test
        void whenTheBotHasWhiteItHasAlreadyMoved() {
            var result = create("BLACK", "PAWN");

            assertThat(result).hasStatus(HttpStatus.CREATED).hasHeader(HttpHeaders.ETAG, "\"1\"");
            assertThat(result).bodyJson()
                    .hasPathSatisfying("$.moves[0].uci", uci -> uci.assertThat().isEqualTo("a2a3"))
                    .hasPathSatisfying("$.lastMove.san", san -> san.assertThat().isEqualTo("a3"))
                    .hasPathSatisfying("$.sideToMove", side -> side.assertThat().isEqualTo("BLACK"));
        }

        @Test
        void needsAColourAndABot() {
            assertThat(mvc.post().uri("/api/v1/games").contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson().extractingPath("$.code").isEqualTo("INVALID_REQUEST");
            assertThat(create("WHITE", "GRANDMASTER")).hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson().extractingPath("$.code").isEqualTo("INVALID_REQUEST");
        }

        @Test
        void anUnavailableBotCreatesNothing() {
            // QUEEN is "down" in the test setup, and with White it must move first.
            assertThat(create("BLACK", "QUEEN")).hasStatus(HttpStatus.SERVICE_UNAVAILABLE)
                    .bodyJson().extractingPath("$.code").isEqualTo("BOT_UNAVAILABLE");
            assertThat(games.count()).isZero();
        }
    }

    @Nested
    class Read {

        @Test
        void getsAGameWithItsVersion() {
            var id = newGame();

            assertThat(mvc.get().uri("/api/v1/games/{id}", id)).hasStatusOk()
                    .hasHeader(HttpHeaders.ETAG, "\"0\"")
                    .bodyJson().extractingPath("$.botProfile").isEqualTo("PAWN");
        }

        @Test
        void anUnknownGameIsA404() {
            assertThat(mvc.get().uri("/api/v1/games/{id}", "11111111-1111-1111-1111-111111111111"))
                    .hasStatus(HttpStatus.NOT_FOUND)
                    .bodyJson().extractingPath("$.code").isEqualTo("GAME_NOT_FOUND");
        }

        @Test
        void anIdThatIsNotAUuidIsA400() {
            assertThat(mvc.get().uri("/api/v1/games/{id}", "nope")).hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson().extractingPath("$.code").isEqualTo("INVALID_REQUEST");
        }

        @Test
        void listsTheGamesNewestFirst() {
            create("WHITE", "PAWN");
            create("WHITE", "KNIGHT");

            assertThat(mvc.get().uri("/api/v1/games?page=0&size=1")).hasStatusOk().bodyJson()
                    .hasPathSatisfying("$.games[0].botProfile", bot -> bot.assertThat().isEqualTo("KNIGHT"))
                    .hasPathSatisfying("$.total", total -> total.assertThat().isEqualTo(2))
                    .hasPathSatisfying("$.totalPages", pages -> pages.assertThat().isEqualTo(2));
        }

        @Test
        void aPageTooBigIsA400() {
            assertThat(mvc.get().uri("/api/v1/games?size=1000")).hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson().extractingPath("$.code").isEqualTo("INVALID_REQUEST");
        }
    }

    @Nested
    class Play {

        @Test
        void playsTheMoveAndTheBotsReply() {
            var id = newGame();

            var result = move(id, "\"0\"", "e2e4");

            assertThat(result).hasStatusOk().hasHeader(HttpHeaders.ETAG, "\"2\"");
            assertThat(result).bodyJson()
                    .hasPathSatisfying("$.moves.length()", n -> n.assertThat().isEqualTo(2))
                    .hasPathSatisfying("$.moves[0].san", san -> san.assertThat().isEqualTo("e4"))
                    .hasPathSatisfying("$.version", version -> version.assertThat().isEqualTo(2));
        }

        @Test
        void acceptsTheVersionWithoutQuotesOrAsAWeakTag() {
            var id = newGame();

            assertThat(move(id, "0", "e2e4")).hasStatusOk();
            assertThat(move(id, "W/\"2\"", "d2d4")).hasStatusOk();
        }

        @Test
        void withoutIfMatchItIsA428() {
            var id = newGame();

            assertThat(move(id, null, "e2e4")).hasStatus(HttpStatus.PRECONDITION_REQUIRED)
                    .bodyJson().extractingPath("$.code").isEqualTo("PRECONDITION_REQUIRED");
        }

        @Test
        void anOldVersionIsA412() {
            var id = newGame();
            move(id, "\"0\"", "e2e4");

            // The same request again: a double click.
            assertThat(move(id, "\"0\"", "e2e4")).hasStatus(HttpStatus.PRECONDITION_FAILED)
                    .bodyJson().extractingPath("$.code").isEqualTo("VERSION_MISMATCH");
        }

        @Test
        void textThatIsNotUciIsA400() {
            var id = newGame();

            assertThat(move(id, "\"0\"", "Nf3")).hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson().extractingPath("$.code").isEqualTo("INVALID_UCI");
        }

        @Test
        void anIllegalMoveIsA422AndChangesNothing() {
            var id = newGame();

            var result = move(id, "\"0\"", "e2e5");

            assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
            assertThat(result).bodyJson()
                    .hasPathSatisfying("$.code", code -> code.assertThat().isEqualTo("ILLEGAL_MOVE"))
                    .hasPathSatisfying("$.status", status -> status.assertThat().isEqualTo(422));
            assertThat(mvc.get().uri("/api/v1/games/{id}", id)).hasHeader(HttpHeaders.ETAG, "\"0\"");
        }

        @Test
        void aFinishedGameIsA409() {
            var id = newGame();
            mvc.post().uri("/api/v1/games/{id}/resign", id).header(HttpHeaders.IF_MATCH, "\"0\"").exchange();

            assertThat(move(id, "\"1\"", "e2e4")).hasStatus(HttpStatus.CONFLICT)
                    .bodyJson().extractingPath("$.code").isEqualTo("GAME_NOT_IN_PROGRESS");
        }
    }

    @Nested
    class ResignGame {

        @Test
        void theBotWins() {
            var id = newGame();

            var result = mvc.post().uri("/api/v1/games/{id}/resign", id).header(HttpHeaders.IF_MATCH, "\"0\"")
                    .exchange();

            assertThat(result).hasStatusOk().hasHeader(HttpHeaders.ETAG, "\"1\"");
            assertThat(result).bodyJson()
                    .hasPathSatisfying("$.status", status -> status.assertThat().isEqualTo("FINISHED"))
                    .hasPathSatisfying("$.outcome.termination", t -> t.assertThat().isEqualTo("RESIGNATION"))
                    .hasPathSatisfying("$.outcome.winner", winner -> winner.assertThat().isEqualTo("BLACK"))
                    .hasPathSatisfying("$.result", r -> r.assertThat().isEqualTo("BLACK_WIN"))
                    .hasPathSatisfying("$.legalMoves.length()", n -> n.assertThat().isEqualTo(0))
                    .hasPathSatisfying("$.finishedAt", at -> at.assertThat().isEqualTo("2026-10-23T12:00:00Z"));
        }

        @Test
        void needsIfMatchToo() {
            var id = newGame();

            assertThat(mvc.post().uri("/api/v1/games/{id}/resign", id))
                    .hasStatus(HttpStatus.PRECONDITION_REQUIRED);
        }
    }

    @Nested
    class Delete {

        @Test
        void theGameIsGoneWithoutIfMatch() {
            var id = newGame();
            move(id, "\"0\"", "e2e4");

            assertThat(mvc.delete().uri("/api/v1/games/{id}", id)).hasStatus(HttpStatus.NO_CONTENT);

            assertThat(mvc.get().uri("/api/v1/games/{id}", id)).hasStatus(HttpStatus.NOT_FOUND);
        }

        @Test
        void anUnknownGameIsA404() {
            assertThat(mvc.delete().uri("/api/v1/games/{id}", "11111111-1111-1111-1111-111111111111"))
                    .hasStatus(HttpStatus.NOT_FOUND)
                    .bodyJson().extractingPath("$.code").isEqualTo("GAME_NOT_FOUND");
        }
    }

    @Nested
    class Languages {

        @Test
        void problemsSpeakTheLanguageOfTheRequest() {
            var id = newGame();

            assertThat(mvc.post().uri("/api/v1/games/{id}/moves", id)
                    .header(HttpHeaders.IF_MATCH, "\"0\"")
                    .header(HttpHeaders.ACCEPT_LANGUAGE, "es-ES,es;q=0.9")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"uci\": \"e2e5\"}"))
                    .bodyJson()
                    .hasPathSatisfying("$.title", title -> title.assertThat().isEqualTo("Jugada ilegal"))
                    .hasPathSatisfying("$.detail", detail -> detail.assertThat()
                            .isEqualTo("e2e5 no es una jugada legal en la posición actual."))
                    .hasPathSatisfying("$.code", code -> code.assertThat().isEqualTo("ILLEGAL_MOVE"));
        }

        @Test
        void englishIsTheDefault() {
            assertThat(mvc.get().uri("/api/v1/games/{id}", "11111111-1111-1111-1111-111111111111")
                    .header(HttpHeaders.ACCEPT_LANGUAGE, "fr-FR"))
                    .bodyJson().extractingPath("$.title").isEqualTo("Game not found");
        }

        @Test
        void botsAreDescribedInTheLanguageOfTheRequest() {
            assertThat(mvc.get().uri("/api/v1/bots").header(HttpHeaders.ACCEPT_LANGUAGE, "es")).hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.length()", n -> n.assertThat().isEqualTo(5))
                    .hasPathSatisfying("$[0].profile", p -> p.assertThat().isEqualTo("PAWN"))
                    .hasPathSatisfying("$[0].level", level -> level.assertThat().isEqualTo(1))
                    .hasPathSatisfying("$[4].description", d -> d.assertThat().asString().startsWith("Stockfish"))
                    .hasPathSatisfying("$[0].description", d -> d.assertThat().asString().startsWith("Juega como"));
        }
    }
}
