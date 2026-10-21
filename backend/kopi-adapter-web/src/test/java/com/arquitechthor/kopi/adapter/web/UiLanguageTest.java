package com.arquitechthor.kopi.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class UiLanguageTest {

    @ParameterizedTest
    @CsvSource({
            "es,                    SPANISH",
            "es-MX,                 SPANISH",
            "en-GB,                 ENGLISH",
            "fr,                    ENGLISH",
            "'fr, es;q=0.8',        SPANISH",
            "'de, en;q=0.9, es;q=0.8', ENGLISH",
    })
    void picksTheBestSupportedLanguageFromAcceptLanguage(String header, UiLanguage expected) {
        assertThat(UiLanguage.fromAcceptLanguage(header)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", ";;;=", "es;q=banana"})
    void fallsBackToEnglishForMissingOrMalformedHeaders(String header) {
        assertThat(UiLanguage.fromAcceptLanguage(header)).isEqualTo(UiLanguage.ENGLISH);
    }

    @ParameterizedTest
    @CsvSource({"es, SPANISH", "es-AR, SPANISH", "en, ENGLISH", "de, ENGLISH"})
    void closestToIgnoresTheRegion(String languageTag, UiLanguage expected) {
        assertThat(UiLanguage.closestTo(Locale.forLanguageTag(languageTag))).isEqualTo(expected);
    }
}
