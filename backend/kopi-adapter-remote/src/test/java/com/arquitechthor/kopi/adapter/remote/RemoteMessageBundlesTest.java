package com.arquitechthor.kopi.adapter.remote;

import static org.assertj.core.api.Assertions.assertThat;

import com.arquitechthor.kopi.domain.game.BotProfile;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Properties;
import org.junit.jupiter.api.Test;

/// Every text of the remote protocol must exist in English and in Spanish,
/// and every bot must have a description.
class RemoteMessageBundlesTest {

    private static final String ENGLISH = "/i18n/remote/messages.properties";
    private static final String SPANISH = "/i18n/remote/messages_es.properties";

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
                assertThat(key).startsWith("remote.");
                assertThat(messages.getProperty(key)).as(key).isNotBlank();
            });
        }
    }

    @Test
    void everyBotIsDescribed() throws IOException {
        var keys = load(ENGLISH).stringPropertyNames();
        assertThat(keys).containsAll(Arrays.stream(BotProfile.values())
                .map(profile -> "remote.bot." + profile + ".description").toList());
    }

    private static Properties load(String resource) throws IOException {
        var properties = new Properties();
        try (var in = RemoteMessageBundlesTest.class.getResourceAsStream(resource)) {
            assertThat(in).as(resource).isNotNull();
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return properties;
    }
}
