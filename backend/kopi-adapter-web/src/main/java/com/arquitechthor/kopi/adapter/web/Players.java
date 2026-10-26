package com.arquitechthor.kopi.adapter.web;

import java.util.Map;

/// How an imported game's players are shown: `Morphy, Paul – Duke Karl`,
/// from its PGN's `White` and `Black` tags.
final class Players {

    private Players() {}

    /// `White – Black`, with `?` for a missing name (as PGN itself writes
    /// it), or empty if the PGN names neither: the page then says
    /// "imported game" instead of `? – ?`.
    static String of(Map<String, String> pgnTags) {
        var white = name(pgnTags, "White");
        var black = name(pgnTags, "Black");
        return white.equals("?") && black.equals("?") ? "" : white + " – " + black;
    }

    private static String name(Map<String, String> pgnTags, String tag) {
        var name = pgnTags.getOrDefault(tag, "?").strip();
        return name.isEmpty() ? "?" : name;
    }
}
