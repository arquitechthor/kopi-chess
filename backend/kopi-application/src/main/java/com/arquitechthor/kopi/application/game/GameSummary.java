package com.arquitechthor.kopi.application.game;

import com.arquitechthor.kopi.domain.chess.Color;
import com.arquitechthor.kopi.domain.game.BotProfile;
import com.arquitechthor.kopi.domain.game.Game;
import com.arquitechthor.kopi.domain.game.GameId;
import com.arquitechthor.kopi.domain.game.GameMode;
import com.arquitechthor.kopi.domain.game.GameOrigin;
import com.arquitechthor.kopi.domain.game.GameOutcome;
import com.arquitechthor.kopi.domain.game.GameResult;
import com.arquitechthor.kopi.domain.game.GameStatus;
import com.arquitechthor.kopi.domain.game.Player;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/// One line of the list of games: enough to recognise a game, without its
/// moves or legal moves.
///
/// @param origin     played here or imported
/// @param mode       against a bot, two people here, online or imported
/// @param white      who plays White; empty for an imported game
/// @param black      who plays Black; empty for an imported game
/// @param humanColor the colour of the one person here (against a bot or
///                   online); empty otherwise
/// @param botProfile the bot, if a bot plays
/// @param plies      how many moves (half-moves) were played
/// @param pgnTags    an imported game's PGN tags (its players…); empty for
///                   a game played here
public record GameSummary(
        GameId id,
        GameOrigin origin,
        GameStatus status,
        GameMode mode,
        Optional<Player> white,
        Optional<Player> black,
        Optional<Color> humanColor,
        Optional<BotProfile> botProfile,
        int plies,
        Optional<GameOutcome> outcome,
        Optional<GameResult> result,
        Instant createdAt,
        Optional<Instant> finishedAt,
        Map<String, String> pgnTags) {

    public GameSummary {
        pgnTags = Collections.unmodifiableMap(new LinkedHashMap<>(pgnTags));
    }

    static GameSummary of(Game game) {
        return new GameSummary(game.id(), game.origin(), game.status(), game.mode(), game.player(Color.WHITE),
                game.player(Color.BLACK), game.humanColor(), game.botProfile(),
                game.moves().size(),
                game.outcome(), game.result(), game.createdAt(), game.finishedAt(), game.pgnTags());
    }
}
