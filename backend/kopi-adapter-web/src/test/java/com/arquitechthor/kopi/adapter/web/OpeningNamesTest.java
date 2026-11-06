package com.arquitechthor.kopi.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpeningNamesTest {

    private static final Locale SPANISH = Locale.of("es");

    private final OpeningNames names = new OpeningNames(Map.of(
            "Ruy Lopez", "Apertura española",
            "Sicilian Defense", "Defensa siciliana",
            "Najdorf Variation", "Variante Najdorf",
            "Morphy Defense", "Defensa Morphy",
            "Queen's Gambit Declined", "Gambito de dama rehusado"));

    @Test
    void aTranslatedFamilyIsShownInSpanish() {
        assertThat(names.in(SPANISH, "Ruy Lopez")).isEqualTo("Apertura española");
        assertThat(names.in(Locale.of("es", "MX"), "Queen's Gambit Declined")).isEqualTo("Gambito de dama rehusado");
    }

    @Test
    void theFamilyAndTheVariationAreTranslatedEachOnItsOwn() {
        assertThat(names.in(SPANISH, "Sicilian Defense: Najdorf Variation"))
                .isEqualTo("Defensa siciliana: Variante Najdorf");
        assertThat(names.in(SPANISH, "Ruy Lopez: Morphy Defense, Closed"))
                .isEqualTo("Apertura española: Defensa Morphy, Closed");
    }

    @Test
    void anUnknownNameFallsBackToEnglish() {
        assertThat(names.in(SPANISH, "Amar Opening: Paris Gambit")).isEqualTo("Amar Opening: Paris Gambit");
    }

    @Test
    void inEnglishNothingChanges() {
        assertThat(names.in(Locale.ENGLISH, "Ruy Lopez")).isEqualTo("Ruy Lopez");
        assertThat(names.in(Locale.FRENCH, "Ruy Lopez")).isEqualTo("Ruy Lopez");
    }

    @Test
    void aWholeNameListedWinsOverItsParts() {
        var withWhole = new OpeningNames(Map.of("Sicilian Defense", "Defensa siciliana",
                "Sicilian Defense: Dragon Variation", "Siciliana, variante del dragón"));

        assertThat(withWhole.in(SPANISH, "Sicilian Defense: Dragon Variation"))
                .isEqualTo("Siciliana, variante del dragón");
    }

    @Test
    void theShippedFileLoadsAndHasTheCommonFamilies() {
        var shipped = new OpeningNames();

        assertThat(shipped.in(SPANISH, "Ruy Lopez")).isEqualTo("Apertura española");
        assertThat(shipped.in(SPANISH, "Sicilian Defense")).isEqualTo("Defensa siciliana");
        assertThat(shipped.in(SPANISH, "Queen's Gambit Declined")).isEqualTo("Gambito de dama rehusado");
        assertThat(OpeningNames.load(OpeningNames.SPANISH_FILE)).hasSizeGreaterThanOrEqualTo(30);
    }

    @Test
    void notesAndBlankLinesAreSkipped() throws IOException {
        assertThat(parse("# a note\n\nRuy Lopez\tApertura española\n")).containsExactly(
                Map.entry("Ruy Lopez", "Apertura española"));
    }

    @Test
    void aMalformedLineIsRefusedWithItsNumber() {
        assertThatIllegalStateException().isThrownBy(() -> parse("# note\nRuy Lopez Apertura española\n"))
                .withMessage("test:2: expected English<TAB>Spanish");
        assertThatIllegalStateException().isThrownBy(() -> parse("Ruy Lopez\t\n"))
                .withMessageContaining("test:1");
    }

    @Test
    void aNameListedTwiceIsRefused() {
        assertThatIllegalStateException().isThrownBy(() -> parse("Ruy Lopez\tA\nRuy Lopez\tB\n"))
                .withMessage("test:2: Ruy Lopez is listed twice");
    }

    private static Map<String, String> parse(String text) throws IOException {
        return OpeningNames.parse(new BufferedReader(new StringReader(text)), "test");
    }
}
