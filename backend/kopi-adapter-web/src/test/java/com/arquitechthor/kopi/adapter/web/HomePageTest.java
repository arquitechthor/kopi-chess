package com.arquitechthor.kopi.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest
class HomePageTest {

    private static final String ENGLISH_TITLE = "Play chess. Understand your games.";
    private static final String SPANISH_TITLE = "Juega al ajedrez. Entiende tus partidas.";

    @Autowired
    MockMvcTester mvc;

    @Test
    void usesTheBrowserLanguageOnTheFirstVisit() {
        assertThat(mvc.get().uri("/").header(HttpHeaders.ACCEPT_LANGUAGE, "es-ES,es;q=0.9"))
                .hasStatusOk()
                .bodyText()
                .contains("<html lang=\"es\">", SPANISH_TITLE);
    }

    @Test
    void fallsBackToEnglishForUnsupportedLanguages() {
        assertThat(mvc.get().uri("/").header(HttpHeaders.ACCEPT_LANGUAGE, "fr-FR"))
                .hasStatusOk()
                .bodyText()
                .contains("<html lang=\"en\">", ENGLISH_TITLE);
    }

    @Test
    void theLanguageSwitchIsRememberedInACookie() {
        assertThat(mvc.get().uri("/?lang=es").header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
                .hasStatusOk()
                .cookies().hasValue(KopiLocaleResolver.COOKIE_NAME, "es");
    }

    @Test
    void theCookieWinsOverTheBrowserLanguage() {
        assertThat(mvc.get().uri("/")
                .header(HttpHeaders.ACCEPT_LANGUAGE, "es")
                .cookie(new Cookie(KopiLocaleResolver.COOKIE_NAME, "en")))
                .hasStatusOk()
                .bodyText()
                .contains(ENGLISH_TITLE);
    }

    @Test
    void unsupportedLanguagesInTheSwitchAreStoredAsEnglish() {
        assertThat(mvc.get().uri("/?lang=de"))
                .hasStatusOk()
                .cookies().hasValue(KopiLocaleResolver.COOKIE_NAME, "en");
    }

    @Test
    void marksTheCurrentLanguageInTheSwitch() {
        assertThat(mvc.get().uri("/?lang=es"))
                .bodyText()
                .containsPattern("hreflang=\"es\"[^>]*aria-current=\"true\"")
                .doesNotContainPattern("hreflang=\"en\"[^>]*aria-current");
    }
}
