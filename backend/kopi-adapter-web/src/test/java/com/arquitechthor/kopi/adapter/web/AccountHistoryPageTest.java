package com.arquitechthor.kopi.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.arquitechthor.kopi.application.account.AccountView;
import com.arquitechthor.kopi.application.history.HistoryView;
import com.arquitechthor.kopi.application.port.KopiOnlineException.Problem;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/// The history in the Kopi account (ADR-021) on the account page, with fakes.
@WebMvcTest
class AccountHistoryPageTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    WebAdapterTestApplication.FakeAccount account;

    @Autowired
    WebAdapterTestApplication.FakeHistory history;

    @BeforeEach
    void signedIn() {
        account.reset();
        history.reset();
        account.view = new AccountView(true, Optional.of("ana@example.com"), Optional.of("Ana_92"), false, false);
    }

    @Test
    void signedOutThereIsNothingAboutIt() {
        account.reset();
        assertThat(mvc.get().uri("/account?lang=en")).bodyText().doesNotContain("/account/history/enable");
    }

    @Test
    void offItOffersToTurnItOnInBothLanguages() {
        assertThat(mvc.get().uri("/account?lang=en")).hasStatusOk().bodyText()
                .contains("Your history in the Kopi account", "Keep my history in the account",
                        "action=\"/account/history/enable\"", "Delete my history from the Kopi account");
        assertThat(mvc.get().uri("/account?lang=es")).bodyText()
                .contains("Tu historial en la cuenta Kopi", "Guardar mi historial en la cuenta");
    }

    @Test
    void turningItOnShowsHowItStands() {
        assertThat(mvc.post().uri("/account/history/enable")).hasStatus(HttpStatus.FOUND).hasRedirectedUrl("/account");

        assertThat(mvc.get().uri("/account?lang=en")).bodyText()
                .contains("3 games in the account, 1 to upload.", "Last sync:", "Sync now", "Stop keeping it")
                .doesNotContain("action=\"/account/history/enable\"");
        assertThat(mvc.get().uri("/account?lang=es")).bodyText().contains("3 partidas en la cuenta, 1 por subir.");
    }

    @Test
    void turningItOnWithoutSessionSaysSo() {
        history.failure = Problem.NOT_SIGNED_IN;

        var result = mvc.post().uri("/account/history/enable").exchange();

        assertThat(result).hasStatus(HttpStatus.FOUND);
        assertThat(result.getMvcResult().getFlashMap().get(AccountController.ERROR)).isEqualTo("NOT_SIGNED_IN");
    }

    @Test
    void syncingAndTurningOffGoBackToTheAccount() {
        history.enable();
        assertThat(mvc.post().uri("/account/history/sync")).hasStatus(HttpStatus.FOUND).hasRedirectedUrl("/account");
        assertThat(history.syncs).isEqualTo(1);

        assertThat(mvc.post().uri("/account/history/disable")).hasStatus(HttpStatus.FOUND);
        assertThat(history.view().enabled()).isFalse();
    }

    @Test
    void aProblemOfTheLastSyncIsShown() {
        history.view = new HistoryView(true, true, false, Optional.empty(), Optional.of("UNREACHABLE"), 0, 2);
        assertThat(mvc.get().uri("/account?lang=en")).bodyText().contains("Kopi cannot be reached right now");
    }

    @Test
    void anotherAccountPausesIt() {
        history.view = new HistoryView(true, true, true, Optional.empty(), Optional.empty(), 0, 0);
        assertThat(mvc.get().uri("/account?lang=en")).bodyText().contains("Paused: it was turned on with another");
    }

    @Test
    void deletingItFromTheAccountConfirms() {
        var result = mvc.post().uri("/account/history/delete").exchange();

        assertThat(result).hasStatus(HttpStatus.FOUND).hasRedirectedUrl("/account");
        assertThat(history.deleted).isTrue();
        assertThat(result.getMvcResult().getFlashMap().get("historyDeleted")).isEqualTo(true);
    }
}
