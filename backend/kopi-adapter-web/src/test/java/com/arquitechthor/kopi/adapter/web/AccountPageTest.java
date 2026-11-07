package com.arquitechthor.kopi.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.arquitechthor.kopi.application.account.AccountView;
import com.arquitechthor.kopi.application.port.KopiOnlineException.Problem;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/// The Kopi account page (ADR-019) with a fake account.
@WebMvcTest
class AccountPageTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    WebAdapterTestApplication.FakeAccount account;

    @BeforeEach
    void signedOut() {
        account.reset();
    }

    @Test
    void signedOutItExplainsAndOffersToSignIn() {
        assertThat(mvc.get().uri("/account?lang=en")).hasStatusOk().bodyText()
                .contains("Kopi account", "Sign in with Kopi", "action=\"/account/login\"",
                        "your password never goes through Kopi Chess");
        assertThat(mvc.get().uri("/account?lang=es")).bodyText().contains("Entrar con Kopi");
    }

    @Test
    void theOnlinePageLinksToIt() {
        assertThat(mvc.get().uri("/online?lang=en")).bodyText().contains("href=\"/account\"", "Kopi account");
    }

    @Test
    void signingInGoesToKopisLoginPage() {
        assertThat(mvc.post().uri("/account/login"))
                .hasStatus(HttpStatus.FOUND).hasRedirectedUrl("https://login.example/oauth2/authorize?state=s1");
    }

    @Test
    void theLoginPageComesBackAndTheSessionStarts() {
        assertThat(mvc.get().uri("/login/oauth2/code/kopi?code=abc&state=s1"))
                .hasStatus(HttpStatus.FOUND).hasRedirectedUrl("/account");
        assertThat(account.lastCode).isEqualTo("abc");
        assertThat(mvc.get().uri("/account?lang=en")).bodyText()
                .contains("Signed in as ana@example.com", "Choose your Kopi id", "name=\"kopiId\"", "Sign out");
    }

    @Test
    void aFailedLoginSaysSo() {
        account.failure = Problem.LOGIN_FAILED;

        assertThat(mvc.get().uri("/login/oauth2/code/kopi?error=access_denied"))
                .hasStatus(HttpStatus.FOUND)
                .flash().containsEntry(AccountController.ERROR, "LOGIN_FAILED");
    }

    @Test
    void reservingTheIdShowsIt() {
        account.view = new AccountView(true, Optional.of("ana@example.com"), Optional.empty(), false, false);

        assertThat(mvc.post().uri("/account/kopi-id").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("kopiId", "Ana_92")).hasStatus(HttpStatus.FOUND).hasRedirectedUrl("/account");
        assertThat(account.reserved).isEqualTo("Ana_92");
        assertThat(mvc.get().uri("/account?lang=en")).bodyText().contains("Ana_92")
                .doesNotContain("Choose your Kopi id");
    }

    @Test
    void aTakenIdIsRefusedWithAMessage() {
        account.view = new AccountView(true, Optional.empty(), Optional.empty(), false, false);
        account.failure = Problem.KOPI_ID_TAKEN;

        assertThat(mvc.post().uri("/account/kopi-id").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("kopiId", "Ana_92"))
                .flash().containsEntry(AccountController.ERROR, "KOPI_ID_TAKEN");
    }

    @Test
    void anAccountPendingApprovalCannotChooseAnIdYet() {
        account.view = new AccountView(true, Optional.of("ana@example.com"), Optional.empty(), true, false);

        assertThat(mvc.get().uri("/account?lang=es")).bodyText()
                .contains("pendiente de que un administrador la apruebe").doesNotContain("Elige tu id de Kopi");
    }

    @Test
    void signingOutEndsTheLoginPageSessionToo() {
        account.view = new AccountView(true, Optional.empty(), Optional.of("Ana_92"), false, false);

        assertThat(mvc.post().uri("/account/logout"))
                .hasStatus(HttpStatus.FOUND).hasRedirectedUrl("https://login.example/logout?client_id=c");
        assertThat(account.view).isEqualTo(AccountView.SIGNED_OUT);
    }
}
