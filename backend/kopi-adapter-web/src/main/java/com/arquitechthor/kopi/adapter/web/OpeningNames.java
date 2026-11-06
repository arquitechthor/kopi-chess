package com.arquitechthor.kopi.adapter.web;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/// The openings' names in the visitor's language. The Lichess dataset only
/// has them in English, and the domain keeps them that way (ADR-011): this
/// is presentation, like [Figurines].
///
/// The Spanish names are in `i18n/web/openings_es.tsv`, not in the message
/// files: they are data, keyed by names with spaces and colons. A dataset
/// name is `Family: Variation, Line`; when the whole name is not listed,
/// each part is looked up on its own and a part not listed stays in
/// English, so the common families read in Spanish even in rare lines.
@Component
class OpeningNames {

    static final String SPANISH_FILE = "i18n/web/openings_es.tsv";

    private final Map<String, String> spanish;

    OpeningNames() {
        this(load(SPANISH_FILE));
    }

    OpeningNames(Map<String, String> spanish) {
        this.spanish = Map.copyOf(spanish);
    }

    /// `english` in `locale`'s language, or as it is when there is no
    /// translation (or the language is English).
    String in(Locale locale, String english) {
        if (UiLanguage.closestTo(locale) != UiLanguage.SPANISH) {
            return english;
        }
        var whole = spanish.get(english);
        if (whole != null) {
            return whole;
        }
        int colon = english.indexOf(": ");
        return colon < 0
                ? parts(english)
                : parts(english.substring(0, colon)) + ": " + parts(english.substring(colon + 2));
    }

    private String parts(String names) {
        return Arrays.stream(names.split(", "))
                .map(name -> spanish.getOrDefault(name, name))
                .collect(Collectors.joining(", "));
    }

    /// `English<TAB>Spanish` per line; blank lines and `#` notes are
    /// skipped. A malformed line stops the application from starting
    /// rather than showing a wrong name.
    static Map<String, String> load(String resource) {
        try (InputStream in = OpeningNames.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing " + resource);
            }
            return parse(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)), resource);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static Map<String, String> parse(BufferedReader lines, String source) throws IOException {
        var names = new HashMap<String, String>();
        int number = 0;
        for (String line = lines.readLine(); line != null; line = lines.readLine()) {
            number++;
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            var fields = line.split("\t", -1);
            if (fields.length != 2 || fields[0].isBlank() || fields[1].isBlank()) {
                throw new IllegalStateException(source + ":" + number + ": expected English<TAB>Spanish");
            }
            if (names.put(fields[0], fields[1]) != null) {
                throw new IllegalStateException(source + ":" + number + ": " + fields[0] + " is listed twice");
            }
        }
        return names;
    }
}
