package com.arquitechthor.kopi.adapter.rest;

import java.util.List;
import java.util.Locale;

/// The language of an API response: the best match for `Accept-Language`
/// among the languages Kopi Chess speaks, English by default.
///
/// Decided here, from the header alone, rather than by the web adapter's
/// cookie-based resolver: API clients send headers, not cookies, and
/// adapters do not depend on each other.
final class ApiLocale {

    static final List<Locale> SUPPORTED = List.of(Locale.ENGLISH, Locale.of("es"));

    private ApiLocale() {}

    static Locale of(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return Locale.ENGLISH;
        }
        try {
            var match = Locale.lookup(Locale.LanguageRange.parse(acceptLanguage), SUPPORTED);
            return match == null ? Locale.ENGLISH : match;
        } catch (IllegalArgumentException _) {
            return Locale.ENGLISH; // a malformed header is not worth an error
        }
    }
}
