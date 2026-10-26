package com.arquitechthor.kopi.application.game;

import com.arquitechthor.kopi.application.port.BotPlayer;
import com.arquitechthor.kopi.domain.game.Game;
import com.arquitechthor.kopi.domain.game.GameStatus;
import java.time.Clock;

/// The bot's move, when it is the bot's turn in a game still in progress.
/// Shared by creating a game (the bot may have White) and playing a move.
final class BotTurn {

    private BotTurn() {}

    /// `game` after the bot's move, or `game` itself if it is not the bot's
    /// turn. The move goes through [Game#play] like any other, so the bot
    /// cannot break the rules either.
    static Game playIfBotToMove(Game game, BotPlayer bot, Clock clock) {
        var botColor = game.botColor();
        if (game.status() != GameStatus.IN_PROGRESS || botColor.isEmpty()
                || game.currentPosition().sideToMove() != botColor.get()) {
            return game;
        }
        var move = bot.chooseMove(game.currentPosition(), game.botProfile().orElseThrow());
        return game.play(move, botColor.get(), clock.instant());
    }
}
