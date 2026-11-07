package com.arquitechthor.kopi.adapter.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.arquitechthor.kopi.application.port.KopiOnlineException.Problem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/// `/api/v1/online` (phase D) with a fake online use case.
@WebMvcTest
class OnlineApiTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    RestAdapterTestApplication.FakeOnline online;

    @BeforeEach
    void reset() {
        online.failure = null;
        online.calls.clear();
    }

    @Test
    void theLobbySaysWhoIPlayAs() {
        assertThat(mvc.get().uri("/api/v1/online")).hasStatusOk().bodyJson()
                .hasPathSatisfying("$.kopiId", id -> assertThat(id).asString().isEqualTo("Ana_92"))
                .hasPathSatisfying("$.received", list -> assertThat(list).asArray().isEmpty());
    }

    @Test
    void anInvitationIsAccepted() {
        assertThat(mvc.post().uri("/api/v1/online/invitations").contentType(MediaType.APPLICATION_JSON)
                .content("{\"kopiId\": \"luis\", \"color\": \"WHITE\"}")).hasStatus(HttpStatus.ACCEPTED);
        assertThat(online.calls).containsExactly("invite luis");
    }

    @Test
    void acceptingAnswersTheNewGame() {
        assertThat(mvc.post().uri("/api/v1/online/invitations/r-1/accept")).hasStatusOk().bodyJson()
                .hasPathSatisfying("$.gameId", id -> assertThat(id).asString().isNotBlank());
        assertThat(mvc.post().uri("/api/v1/online/invitations/r-1/decline")).hasStatus(HttpStatus.NO_CONTENT);
    }

    @Test
    void theRelaysRefusalsHaveTheirStatus() {
        online.failure = Problem.KOPI_ID_NOT_FOUND;
        assertThat(mvc.post().uri("/api/v1/online/invitations").contentType(MediaType.APPLICATION_JSON)
                .content("{\"kopiId\": \"nadie\"}")).hasStatus(HttpStatus.NOT_FOUND).bodyJson()
                .hasPathSatisfying("$.onlineProblem", p -> assertThat(p).asString().isEqualTo("KOPI_ID_NOT_FOUND"));

        online.failure = Problem.UNREACHABLE;
        assertThat(mvc.post().uri("/api/v1/online/invitations/r-1/cancel")).hasStatus(HttpStatus.BAD_GATEWAY);

        online.failure = Problem.NOT_SIGNED_IN;
        assertThat(mvc.post().uri("/api/v1/online/invitations/r-1/dismiss")).hasStatus(HttpStatus.UNAUTHORIZED);
    }
}
