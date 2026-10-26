package com.arquitechthor.kopi.application.port;

import com.arquitechthor.kopi.domain.game.Game;
import com.arquitechthor.kopi.domain.game.GameOrigin;
import com.arquitechthor.kopi.domain.game.GameResult;
import com.arquitechthor.kopi.domain.game.GameStatus;
import java.util.Objects;
import java.util.Optional;

/// Which games a list shows (plan §9, the history): by origin and by
/// result. An empty criterion lets every game through.
///
/// [#matches] says what the criteria mean; the database adapter must give
/// the same answer in SQL.
///
/// @param origin played here or imported
/// @param result how it stands: a result, or still in progress
public record GameFilter(Optional<GameOrigin> origin, Optional<ResultFilter> result) {

    /// Every game.
    public static final GameFilter ALL = new GameFilter(Optional.empty(), Optional.empty());

    public GameFilter {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(result, "result");
    }

    /// A game's result as a filter sees it: the score, or not finished yet.
    public enum ResultFilter {
        WHITE_WIN,
        BLACK_WIN,
        DRAW,
        IN_PROGRESS;

        /// Whether `game` stands like this.
        boolean matches(Game game) {
            return switch (this) {
                case IN_PROGRESS -> game.status() == GameStatus.IN_PROGRESS;
                case WHITE_WIN -> game.result().equals(Optional.of(GameResult.WHITE_WIN));
                case BLACK_WIN -> game.result().equals(Optional.of(GameResult.BLACK_WIN));
                case DRAW -> game.result().equals(Optional.of(GameResult.DRAW));
            };
        }
    }

    /// Whether `game` passes every criterion.
    public boolean matches(Game game) {
        return origin.map(wanted -> game.origin() == wanted).orElse(true)
                && result.map(wanted -> wanted.matches(game)).orElse(true);
    }
}
