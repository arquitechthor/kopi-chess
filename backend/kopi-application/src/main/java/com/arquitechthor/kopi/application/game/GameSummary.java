package com.arquitechthor.kopi.application.game;

import com.arquitechthor.kopi.domain.chess.Color;
import com.arquitechthor.kopi.domain.game.BotProfile;
import com.arquitechthor.kopi.domain.game.Game;
import com.arquitechthor.kopi.domain.game.GameId;
import com.arquitechthor.kopi.domain.game.GameOutcome;
import com.arquitechthor.kopi.domain.game.GameResult;
import com.arquitechthor.kopi.domain.game.GameStatus;
import java.time.Instant;
import java.util.Optional;

/// One line of the list of games: enough to recognise a game, without its
/// moves or legal moves.
///
/// @param plies how many moves (half-moves) were played
public record GameSummary(
        GameId id,
        GameStatus status,
        Color humanColor,
        BotProfile botProfile,
        int plies,
        Optional<GameOutcome> outcome,
        Optional<GameResult> result,
        Instant createdAt,
        Optional<Instant> finishedAt) {

    static GameSummary of(Game game) {
        return new GameSummary(game.id(), game.status(), game.humanColor(), game.botProfile(), game.moves().size(),
                game.outcome(), game.result(), game.createdAt(), game.finishedAt());
    }
}
