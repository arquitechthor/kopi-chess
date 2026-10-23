package com.arquitechthor.kopi.adapter.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.arquitechthor.kopi.domain.game.BotProfile;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Properties;
import org.junit.jupiter.api.Test;

/// Every API text must exist in English and in Spanish, and every bot must
/// have a description.
class RestMessageBundlesTest {

    private static final String ENGLISH = "/i18n/rest/messages.properties";
    private static final String SPANISH = "/i18n/rest/messages_es.properties";

    @Test
    void englishAndSpanishHaveTheSameKeys() throws IOException {
        assertThat(load(SPANISH).stringPropertyNames())
                .containsExactlyInAnyOrderElementsOf(load(ENGLISH).stringPropertyNames());
    }

    @Test
    void everyKeyIsNamespacedAndNoneIsBlank() throws IOException {
        for (var bundle : new String[] {ENGLISH, SPANISH}) {
            var messages = load(bundle);
            assertThat(messages.stringPropertyNames()).as(bundle).allSatisfy(key -> {
                assertThat(key).startsWith("rest.");
                assertThat(messages.getProperty(key)).as(key).isNotBlank();
            });
        }
    }

    @Test
    void everyBotIsDescribed() throws IOException {
        var keys = load(ENGLISH).stringPropertyNames();

        assertThat(Arrays.stream(BotProfile.values()).map(profile -> "rest.bot." + profile + ".description"))
                .allSatisfy(key -> assertThat(keys).contains(key));
    }

    private static Properties load(String resource) throws IOException {
        try (var in = RestMessageBundlesTest.class.getResourceAsStream(resource)) {
            assertThat(in).as(resource).isNotNull();
            var properties = new Properties();
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            return properties;
        }
    }
}
