package com.arquitechthor.kopi.domain.game;

import java.util.Objects;
import java.util.regex.Pattern;

/// Who plays one colour of a game (ADR-018): a bot, a person at this
/// computer, or a friend online.
///
/// A sealed interface, so a `switch` over a player handles the three kinds
/// and a new kind would not compile until every adapter knows it.
public sealed interface Player permits Player.Bot, Player.Local, Player.Remote {

    /// One of Kopi's bots.
    record Bot(BotProfile profile) implements Player {
        public Bot {
            Objects.requireNonNull(profile, "profile");
        }
    }

    /// A person at this computer. Against a bot the person has no name
    /// (`""`); in a game between two people here each one has a name.
    ///
    /// @param name trimmed, at most [#MAX_NAME] characters; may be empty
    record Local(String name) implements Player {

        /// The longest name a person may give.
        public static final int MAX_NAME = 30;

        /// The person against a bot, or "me" in an online game.
        public static final Local YOU = new Local("");

        public Local {
            name = name == null ? "" : name.strip();
            if (name.length() > MAX_NAME) {
                throw new IllegalArgumentException("A name has at most " + MAX_NAME + " characters: " + name);
            }
        }

        /// Whether the person gave a name.
        public boolean named() {
            return !name.isEmpty();
        }
    }

    /// A friend playing online from their own Kopi Chess, known by their
    /// public Kopi id (ADR-019). Their moves arrive through the relay.
    ///
    /// @param kopiId 3 to 20 letters without accents, digits or `_`
    record Remote(String kopiId) implements Player {

        private static final Pattern KOPI_ID = Pattern.compile("[A-Za-z0-9_]{3,20}");

        public Remote {
            Objects.requireNonNull(kopiId, "kopiId");
            if (!KOPI_ID.matcher(kopiId).matches()) {
                throw new IllegalArgumentException("Not a Kopi id: " + kopiId);
            }
        }
    }
}
