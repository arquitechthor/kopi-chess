package com.arquitechthor.kopi.adapter.web;

import com.arquitechthor.kopi.domain.game.Player;
import java.util.Map;
import java.util.Optional;

/// How the players of a game with no bot are shown: `Morphy, Paul – Duke
/// Karl`, from an imported game's PGN `White` and `Black` tags, or `Ana –
/// Luis` for two people at this computer.
final class Players {

    private Players() {}

    /// `White – Black` for a game between two people here, from the names
    /// they gave.
    static String local(Optional<Player> white, Optional<Player> black) {
        return name(white) + " – " + name(black);
    }

    /// A person's name, or a friend's Kopi id; empty for a bot or nobody.
    static String name(Optional<Player> player) {
        return switch (player.orElse(null)) {
            case Player.Local local -> local.name();
            case Player.Remote remote -> remote.kopiId();
            case Player.Bot _ -> "";
            case null -> "";
        };
    }

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
