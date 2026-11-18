package com.arquitechthor.kopi.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/// The scoresheet: the game's details and 60 numbered moves, a box for
/// White and one for Black, reached from the "Analysis" menu.
@WebMvcTest
class ScoresheetPageTest {

    @Autowired
    MockMvcTester mvc;

    @Test
    void showsTheGamesDetailsAndSixtyNumberedMoves() {
        assertThat(mvc.get().uri("/scoresheet?lang=es"))
                .hasStatusOk()
                .bodyText()
                .contains("Plantilla de anotación", "Evento", "Fecha", "Blancas", "Negras",
                        "name=\"event\"", "name=\"date\"", "name=\"white\"", "name=\"black\"",
                        "name=\"w1\"", "name=\"b1\"", "name=\"w60\"", "name=\"b60\"",
                        "/img/logo.svg", "/js/scoresheet.js")
                .doesNotContain("name=\"w61\"");
    }

    @Test
    void eachMoveBoxSaysWhichMoveItIs() {
        assertThat(mvc.get().uri("/scoresheet?lang=en"))
                .bodyText()
                .contains("aria-label=\"White&#39;s move 12\"", "aria-label=\"Black&#39;s move 12\"");
    }

    @Test
    void theMenuGroupsPlayAndAnalysis() {
        assertThat(mvc.get().uri("/?lang=es"))
                .bodyText()
                .containsPattern("(?s)>Jugar</button>.*"
                        + "<a href=\"/#new-game\">Contra bot \\(local\\)</a>\\s*"
                        + "<a href=\"/online\">Contra amigo \\(online\\)</a>\\s*"
                        + "<a href=\"/#local-game\">Contra amigo \\(local\\)</a>")
                .containsPattern("(?s)>Análisis</button>.*"
                        + "<a href=\"/games\">Historial</a>\\s*"
                        + "<a href=\"/games/import\">Importar PGN</a>\\s*"
                        + "<a href=\"/scoresheet\">Plantilla de anotación</a>");
    }
}
