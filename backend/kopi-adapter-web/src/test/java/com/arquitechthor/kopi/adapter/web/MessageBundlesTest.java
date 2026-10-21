package com.arquitechthor.kopi.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.junit.jupiter.api.Test;

/// Every UI text must exist in English and in Spanish.
class MessageBundlesTest {

    private static final String ENGLISH = "/i18n/web/messages.properties";
    private static final String SPANISH = "/i18n/web/messages_es.properties";

    @Test
    void englishAndSpanishHaveTheSameKeys() throws IOException {
        var english = load(ENGLISH);
        var spanish = load(SPANISH);

        assertThat(spanish.stringPropertyNames())
                .as("keys in %s and %s", SPANISH, ENGLISH)
                .containsExactlyInAnyOrderElementsOf(english.stringPropertyNames());
    }

    @Test
    void noTranslationIsBlank() throws IOException {
        for (var bundle : new String[] {ENGLISH, SPANISH}) {
            var messages = load(bundle);
            assertThat(messages.stringPropertyNames())
                    .as("blank values in %s", bundle)
                    .allSatisfy(key -> assertThat(messages.getProperty(key)).as(key).isNotBlank());
        }
    }

    private static Properties load(String resource) throws IOException {
        try (var in = MessageBundlesTest.class.getResourceAsStream(resource)) {
            assertThat(in).as(resource).isNotNull();
            var properties = new Properties();
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            return properties;
        }
    }
}
