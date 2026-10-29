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
    void sharesTheMenuAndFooterOfTheFamilysSites() {
        // Design §11.6: "Home" first and "About me" last in the menu, and
        // the closing line Kopi Tools · Apuntes AWS · Kopi Chess · About me in the footer.
        assertThat(mvc.get().uri("/?lang=es"))
                .hasStatusOk()
                .bodyText()
                .containsPattern("(?s)class=\"nav-links\">\\s*<a href=\"/\">Inicio</a>.*"
                        + "<a href=\"https://arquitechthor.github.io/\">Sobre mí</a>\\s*<span class=\"language-switch\"")
                .containsPattern("(?s)class=\"footer-sitios\".*"
                        + "<a href=\"https://kopitools.link\">Kopi Tools</a>\\s*"
                        + "<a href=\"https://arquitechthor.github.io/aws-cert-study/\">Apuntes AWS</a>\\s*"
                        + "<a href=\"https://arquitechthor.github.io/#kopi-chess\">Kopi Chess</a>\\s*"
                        + "<a href=\"https://arquitechthor.github.io/\">Sobre mí</a>")
                .contains("/css/kopi.css", "/js/nav.js", "/img/logo.svg");
    }

    @Test
    void marksTheCurrentLanguageInTheSwitch() {
        assertThat(mvc.get().uri("/?lang=es"))
                .bodyText()
                .containsPattern("hreflang=\"es\"[^>]*aria-current=\"true\"")
                .doesNotContainPattern("hreflang=\"en\"[^>]*aria-current");
    }
}
