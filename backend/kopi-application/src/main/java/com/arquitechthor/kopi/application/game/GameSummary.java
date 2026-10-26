package com.arquitechthor.kopi.application.game;

import com.arquitechthor.kopi.domain.chess.Color;
import com.arquitechthor.kopi.domain.game.BotProfile;
import com.arquitechthor.kopi.domain.game.Game;
import com.arquitechthor.kopi.domain.game.GameId;
import com.arquitechthor.kopi.domain.game.GameOrigin;
import com.arquitechthor.kopi.domain.game.GameOutcome;
import com.arquitechthor.kopi.domain.game.GameResult;
import com.arquitechthor.kopi.domain.game.GameStatus;
import java.time.Instant;
import java.util.Optional;

/// One line of the list of games: enough to recognise a game, without its
/// moves or legal moves.
///
/// @param origin     played here or imported
/// @param humanColor the person's colour; empty for an imported game
/// @param botProfile the opponent; empty for an imported game
/// @param plies      how many moves (half-moves) were played
public record GameSummary(
        GameId id,
        GameOrigin origin,
        GameStatus status,
        Optional<Color> humanColor,
        Optional<BotProfile> botProfile,
        int plies,
        Optional<GameOutcome> outcome,
        Optional<GameResult> result,
        Instant createdAt,
        Optional<Instant> finishedAt) {

    static GameSummary of(Game game) {
        return new GameSummary(game.id(), game.origin(), game.status(), game.humanColor(), game.botProfile(),
                game.moves().size(),
                game.outcome(), game.result(), game.createdAt(), game.finishedAt());
    }
}
