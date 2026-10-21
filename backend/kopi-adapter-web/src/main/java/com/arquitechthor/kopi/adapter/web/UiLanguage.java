package com.arquitechthor.kopi.adapter.web;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/// The languages the web UI is translated into.
///
/// English is the default: it is used whenever the visitor asks for a
/// language Kopi Chess does not have.
enum UiLanguage {
    ENGLISH(Locale.ENGLISH),
    SPANISH(Locale.of("es"));

    private static final List<Locale> LOCALES = Arrays.stream(values()).map(UiLanguage::locale).toList();

    private final Locale locale;

    UiLanguage(Locale locale) {
        this.locale = locale;
    }

    Locale locale() {
        return locale;
    }

    /// The supported language closest to `requested`, ignoring the region
    /// (`es-MX` → Spanish, `fr` → English).
    static UiLanguage closestTo(Locale requested) {
        return Arrays.stream(values())
                .filter(language -> language.locale.getLanguage().equals(requested.getLanguage()))
                .findFirst()
                .orElse(ENGLISH);
    }

    /// Picks a language from an `Accept-Language` header, honouring its
    /// priorities (`fr, es;q=0.8` → Spanish). Missing or malformed headers
    /// fall back to English.
    static UiLanguage fromAcceptLanguage(String header) {
        if (header == null || header.isBlank()) {
            return ENGLISH;
        }
        try {
            var match = Locale.lookup(Locale.LanguageRange.parse(header), LOCALES);
            return match == null ? ENGLISH : closestTo(match);
        } catch (IllegalArgumentException _) {
            return ENGLISH;
        }
    }
}
