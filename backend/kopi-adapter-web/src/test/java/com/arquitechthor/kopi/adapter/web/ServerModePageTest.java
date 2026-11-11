package com.arquitechthor.kopi.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.arquitechthor.kopi.application.account.AccountView;
import com.arquitechthor.kopi.application.servermode.ServerModeView;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/// Server mode (ADR-022) on the account page and the warning on every
/// page, with fakes.
@WebMvcTest
class ServerModePageTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    WebAdapterTestApplication.FakeAccount account;

    @Autowired
    WebAdapterTestApplication.FakeServerMode serverMode;

    @BeforeEach
    void signedInAndOff() {
        account.reset();
        account.view = new AccountView(true, Optional.of("ana@example.com"), Optional.of("Ana_92"), false, false);
        serverMode.view = new ServerModeView(false, true, false, "DESKTOP-ANA");
    }

    @Test
    void offItOffersToTurnItOnWithThisComputersNameInBothLanguages() {
        assertThat(mvc.get().uri("/account?lang=en")).hasStatusOk().bodyText()
                .contains("Play from your phone", "Turn on server mode", "action=\"/account/server-mode/enable\"",
                        "value=\"DESKTOP-ANA\"")
                .doesNotContain("server-mode-banner", "Turn off server mode");
        assertThat(mvc.get().uri("/account?lang=es")).bodyText()
                .contains("Jugar desde el móvil", "Encender el modo servidor");
    }

    @Test
    void signedOutThereIsNothingAboutIt() {
        account.reset();
        assertThat(mvc.get().uri("/account?lang=en")).bodyText()
                .doesNotContain("/account/server-mode/enable");
    }

    @Test
    void turningItOnGoesBackToTheSectionAndShowsHowItStands() {
        var result = mvc.post().uri("/account/server-mode/enable").param("name", "PC de casa").exchange();

        assertThat(result).hasStatus(HttpStatus.FOUND).hasRedirectedUrl("/account#server-mode");
        assertThat(mvc.get().uri("/account?lang=en")).bodyText()
                .contains("your phone sees this computer as “PC de casa”", "Turn off server mode", "Change the name",
                        "action=\"/account/server-mode/name\"")
                .doesNotContain("action=\"/account/server-mode/enable\"");
        assertThat(mvc.get().uri("/account?lang=es")).bodyText().contains("tu móvil ve este equipo como «PC de casa»");
    }

    @Test
    void whileOnEveryPageWarns() {
        serverMode.view = new ServerModeView(true, true, false, "PC de casa");

        assertThat(mvc.get().uri("/?lang=en")).bodyText()
                .contains("server-mode-banner", "Server mode is on: your phone can play", "/account#server-mode");
        assertThat(mvc.get().uri("/games?lang=es")).bodyText().contains("Modo servidor encendido");
    }

    @Test
    void pausedThereIsNoWarningButTheAccountPageSaysWhy() {
        serverMode.view = new ServerModeView(true, true, true, "PC de casa");

        assertThat(mvc.get().uri("/?lang=en")).bodyText().doesNotContain("server-mode-banner");
        assertThat(mvc.get().uri("/account?lang=en")).bodyText()
                .contains("Paused: it was turned on with another Kopi account");
    }

    @Test
    void renamingAndTurningOffGoBackToTheSection() {
        serverMode.enable("PC de casa");

        assertThat(mvc.post().uri("/account/server-mode/name").param("name", "Portátil"))
                .hasStatus(HttpStatus.FOUND).hasRedirectedUrl("/account#server-mode");
        assertThat(serverMode.view().name()).isEqualTo("Portátil");

        assertThat(mvc.post().uri("/account/server-mode/disable")).hasStatus(HttpStatus.FOUND);
        assertThat(serverMode.view().enabled()).isFalse();
    }
}
