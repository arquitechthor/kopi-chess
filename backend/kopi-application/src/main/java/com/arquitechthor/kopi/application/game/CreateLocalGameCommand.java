package com.arquitechthor.kopi.application.game;

import com.arquitechthor.kopi.domain.game.Player;

/// Start a game between two people at this computer: `whiteName` plays
/// White and `blackName` Black (ADR-018).
///
/// A command that exists is valid: both names are trimmed, not empty and
/// at most [Player.Local#MAX_NAME] characters.
///
/// @throws InvalidPlayerNameException if a name is empty or too long
public record CreateLocalGameCommand(String whiteName, String blackName) {

    public CreateLocalGameCommand {
        whiteName = valid(whiteName, "White");
        blackName = valid(blackName, "Black");
    }

    private static String valid(String name, String side) {
        var trimmed = name == null ? "" : name.strip();
        if (trimmed.isEmpty()) {
            throw new InvalidPlayerNameException(side + " needs a name");
        }
        if (trimmed.length() > Player.Local.MAX_NAME) {
            throw new InvalidPlayerNameException(side + "'s name has more than " + Player.Local.MAX_NAME
                    + " characters");
        }
        return trimmed;
    }
}
